package com.sma.admin.mail.internal.service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.annotation.PreDestroy;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import javax.net.ssl.SSLException;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.smtp.SMTPTransport;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.sma.admin.common.security.*;
import com.sma.admin.common.deployment.DeploymentWork;

/** One recipient per bounded TLS transaction. Never logs the provider response, credentials or message. */
@Component
public class SmtpMailSender {
    private final SmtpProperties settings;private final SmtpNetworkPolicy network;private final TenantSecretCodec secrets;
    private final ExecutorService executor;
    @org.springframework.beans.factory.annotation.Autowired
    public SmtpMailSender(SmtpProperties settings,SmtpNetworkPolicy network,TenantSecretCodec secrets,MailWorkProperties work){
        this(settings,network,secrets,work,new ThreadPoolExecutor(settings.maximumConcurrent(),settings.maximumConcurrent(),0,TimeUnit.SECONDS,
                new SynchronousQueue<>(),Thread.ofPlatform().daemon().inheritInheritableThreadLocals(false).name("sma-smtp-",0).factory(),new ThreadPoolExecutor.AbortPolicy()));
    }
    SmtpMailSender(SmtpProperties settings,SmtpNetworkPolicy network,TenantSecretCodec secrets,MailWorkProperties work,ExecutorService executor){
        this.settings=settings;this.network=network;this.secrets=secrets;this.executor=executor;
        if(settings.operationTimeout().plusSeconds(5).compareTo(work.leaseDuration())>=0)
            throw new IllegalArgumentException("SMTP deadline must fit inside the worker lease");
    }
    /** Takes ownership of the transport branch, including validation/rejection and cancellation before execution. */
    public MailWorkService.Outcome send(MailWorkService.Prepared prepared,DeploymentWork.Permit completion){
        TrackedTask future=null;
        try{
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("SMTP cannot run inside a database transaction");
        ActorContext.requireTenant(prepared.claim().tenantId());
        if(ActorContext.require().kind()!=ActorRef.Kind.SYSTEM_JOB)throw new org.springframework.security.access.AccessDeniedException("SMTP worker identity required");
        var exchange=new Exchange();var socket=new AtomicReference<PinnedSmtpSocketFactory>();
        future=new TrackedTask(()->ActorContext.withinJob(prepared.claim().tenantId(),"smtp:"+prepared.claim().deliveryId(),
                ()->perform(prepared,exchange,socket)),completion);
        try{executor.execute(future);}
        catch(RejectedExecutionException full){future.cancel(false);return MailWorkService.Outcome.RETRYABLE;}
        catch(RuntimeException|Error failure){future.cancel(false);throw failure;}
        try{return future.get(settings.operationTimeout().toMillis(),TimeUnit.MILLISECONDS);}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();return expire(future,exchange,socket);}
        catch(TimeoutException timeout){return expire(future,exchange,socket);}
        catch(CancellationException cancelled){return expire(future,exchange,socket);}
        catch(ExecutionException failure){return exchange.started()?MailWorkService.Outcome.UNKNOWN:MailWorkService.Outcome.RETRYABLE;}
        }finally{if(future==null)completion.close();}
    }
    private MailWorkService.Outcome expire(Future<?> future,Exchange exchange,AtomicReference<PinnedSmtpSocketFactory> socket){
        boolean started=exchange.expire();var current=socket.get();if(current!=null)current.close();future.cancel(true);
        return started?MailWorkService.Outcome.UNKNOWN:MailWorkService.Outcome.RETRYABLE;
    }
    private MailWorkService.Outcome perform(MailWorkService.Prepared prepared,Exchange exchange,
                                            AtomicReference<PinnedSmtpSocketFactory> sockets){
        SMTPTransport transport=null;PinnedSmtpSocketFactory factory=null;char[] password=null;
        try{
            var profile=prepared.profile();
            if(!Objects.equals(profile.getTenantId(),prepared.claim().tenantId()) || !Set.of("TLS","STARTTLS").contains(profile.getTlsMode()))
                return MailWorkService.Outcome.FAILED;
            var address=network.authorize(profile.getHost(),profile.getPort());
            if(exchange.expired())return MailWorkService.Outcome.RETRYABLE;
            factory=new PinnedSmtpSocketFactory(address,profile.getPort());sockets.set(factory);
            if(exchange.expired()){factory.close();return MailWorkService.Outcome.RETRYABLE;}
            Properties properties=new Properties();properties.setProperty("mail.smtp.auth","true");
            properties.setProperty("mail.smtp.auth.mechanisms","LOGIN PLAIN");
            properties.setProperty("mail.smtp.ssl.enable",Boolean.toString(profile.getTlsMode().equals("TLS")));
            properties.setProperty("mail.smtp.starttls.enable",Boolean.toString(profile.getTlsMode().equals("STARTTLS")));
            properties.setProperty("mail.smtp.starttls.required",Boolean.toString(profile.getTlsMode().equals("STARTTLS")));
            properties.setProperty("mail.smtp.ssl.checkserveridentity","true");
            properties.setProperty("mail.smtp.ssl.protocols","TLSv1.3 TLSv1.2");
            properties.put("mail.smtp.socketFactory",factory);properties.setProperty("mail.smtp.socketFactory.fallback","false");
            properties.setProperty("mail.smtp.connectiontimeout",Long.toString(settings.connectTimeout().toMillis()));
            properties.setProperty("mail.smtp.timeout",Long.toString(settings.readTimeout().toMillis()));
            properties.setProperty("mail.smtp.writetimeout",Long.toString(settings.writeTimeout().toMillis()));
            properties.setProperty("mail.smtp.quitwait","false");properties.setProperty("mail.smtp.sendpartial","false");
            properties.setProperty("mail.smtp.localhost","sma.local");properties.setProperty("mail.debug","false");
            Session session=Session.getInstance(properties);session.setDebug(false);
            var message=new MimeMessage(session){@Override protected void updateMessageID()throws MessagingException{setHeader("Message-ID","<"+prepared.messageId()+">");}};
            message.setFrom(new InternetAddress(profile.getFromAddress(),profile.getFromName(),StandardCharsets.UTF_8.name()));
            InternetAddress recipient=new InternetAddress(prepared.payload().recipient(),true);recipient.validate();
            message.setRecipient(Message.RecipientType.TO,recipient);message.setSubject(prepared.payload().subject(),StandardCharsets.UTF_8.name());
            message.setText(prepared.payload().body(),StandardCharsets.UTF_8.name(),prepared.payload().html()?"html":"plain");message.saveChanges();
            try(var secret=secrets.decrypt(new TenantConfigurationRef(profile.getTenantId(),"mail",profile.getProfileId(),profile.getConfigurationVersion(),profile.getCredentialVersion()),
                    "smtp-password",profile.getSecretEnvelope())){password=secret.copy();}
            transport=(SMTPTransport)session.getTransport("smtp");
            transport.connect(profile.getHost(),profile.getPort(),profile.getUsername(),new String(password));
            Arrays.fill(password,'\0');password=null;
            if(!exchange.beginData())return MailWorkService.Outcome.RETRYABLE;
            transport.sendMessage(message,message.getAllRecipients());return MailWorkService.Outcome.ACCEPTED;
        }catch(SmtpNetworkPolicy.PolicyDenied denied){return MailWorkService.Outcome.FAILED;}
        catch(AuthenticationFailedException invalid){return MailWorkService.Outcome.FAILED;}
        catch(Exception failure){return classify(failure,exchange.started());}
        finally{
            if(password!=null)Arrays.fill(password,'\0');
            if(factory!=null)factory.close();
            if(transport!=null)try{transport.close();}catch(MessagingException ignored){}
        }
    }
    static MailWorkService.Outcome classify(Throwable failure,boolean started){
        // Provider text may include addresses or credential diagnostics; classify only typed status codes.
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Throwable e=failure;e!=null && seen.add(e);e=e instanceof MessagingException m&&m.getNextException()!=null?m.getNextException():e.getCause()){
            if(e instanceof SSLException || e instanceof AddressException)return MailWorkService.Outcome.FAILED;
            int code=e instanceof SMTPSendFailedException smtp?smtp.getReturnCode():e instanceof SMTPAddressFailedException address?address.getReturnCode():0;
            if(code>=500&&code<600)return MailWorkService.Outcome.FAILED;
            if(code>=400&&code<500)return MailWorkService.Outcome.RETRYABLE;
        }
        return started?MailWorkService.Outcome.UNKNOWN:MailWorkService.Outcome.RETRYABLE;
    }
    /** FutureTask.done runs on cancellation even while I/O is still alive; only run's finally can release a running branch. */
    static final class TrackedTask extends FutureTask<MailWorkService.Outcome> {
        private final AtomicInteger state=new AtomicInteger(); // 0 pending, 1 executing, 2 physically finished
        private final DeploymentWork.Permit completion;
        TrackedTask(Callable<MailWorkService.Outcome> task,DeploymentWork.Permit completion){super(task);this.completion=completion;}
        @Override public void run(){
            if(!state.compareAndSet(0,1))return;
            try{super.run();}finally{state.set(2);completion.close();}
        }
        @Override protected void done(){if(state.compareAndSet(0,2))completion.close();}
    }
    /** Timeout must win before DATA or conservatively report UNKNOWN; a RETRYABLE timeout cannot subsequently send. */
    static final class Exchange {
        private boolean started,expired;
        synchronized boolean beginData(){if(expired)return false;started=true;return true;}
        synchronized boolean expire(){expired=true;return started;}
        synchronized boolean started(){return started;}
        synchronized boolean expired(){return expired;}
    }
    @PreDestroy public void close(){for(var queued:executor.shutdownNow())if(queued instanceof Future<?> task)task.cancel(false);}
}
