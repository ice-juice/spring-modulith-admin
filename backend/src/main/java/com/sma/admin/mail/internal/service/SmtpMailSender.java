package com.sma.admin.mail.internal.service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
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

/** One recipient per bounded TLS transaction. Never logs the provider response, credentials or message. */
@Component
public class SmtpMailSender {
    private final SmtpProperties settings;private final SmtpNetworkPolicy network;private final TenantSecretCodec secrets;
    private final ExecutorService executor;
    public SmtpMailSender(SmtpProperties settings,SmtpNetworkPolicy network,TenantSecretCodec secrets,MailWorkProperties work){
        this.settings=settings;this.network=network;this.secrets=secrets;
        if(settings.operationTimeout().plusSeconds(5).compareTo(work.leaseDuration())>=0)
            throw new IllegalArgumentException("SMTP deadline must fit inside the worker lease");
        executor=new ThreadPoolExecutor(settings.maximumConcurrent(),settings.maximumConcurrent(),0,TimeUnit.SECONDS,
                new SynchronousQueue<>(),Thread.ofPlatform().daemon().name("sma-smtp-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
    }
    public MailWorkService.Outcome send(MailWorkService.Prepared prepared){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("SMTP cannot run inside a database transaction");
        ActorContext.requireTenant(prepared.claim().tenantId());
        if(ActorContext.require().kind()!=ActorRef.Kind.SYSTEM_JOB)throw new org.springframework.security.access.AccessDeniedException("SMTP worker identity required");
        var started=new AtomicBoolean();var expired=new AtomicBoolean();var socket=new AtomicReference<PinnedSmtpSocketFactory>();
        Future<MailWorkService.Outcome> future;
        try{future=executor.submit(()->ActorContext.withinJob(prepared.claim().tenantId(),"smtp:"+prepared.claim().deliveryId(),
                ()->perform(prepared,started,expired,socket)));}
        catch(RejectedExecutionException full){return MailWorkService.Outcome.RETRYABLE;}
        try{return future.get(settings.operationTimeout().toMillis(),TimeUnit.MILLISECONDS);}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();return expire(future,started,expired,socket);}
        catch(TimeoutException timeout){return expire(future,started,expired,socket);}
        catch(ExecutionException failure){return started.get()?MailWorkService.Outcome.UNKNOWN:MailWorkService.Outcome.RETRYABLE;}
    }
    private MailWorkService.Outcome expire(Future<?> future,AtomicBoolean started,AtomicBoolean expired,AtomicReference<PinnedSmtpSocketFactory> socket){
        expired.set(true);var current=socket.get();if(current!=null)current.close();future.cancel(true);
        return started.get()?MailWorkService.Outcome.UNKNOWN:MailWorkService.Outcome.RETRYABLE;
    }
    private MailWorkService.Outcome perform(MailWorkService.Prepared prepared,AtomicBoolean started,AtomicBoolean expired,
                                            AtomicReference<PinnedSmtpSocketFactory> sockets){
        SMTPTransport transport=null;PinnedSmtpSocketFactory factory=null;char[] password=null;
        try{
            var profile=prepared.profile();
            if(!Objects.equals(profile.getTenantId(),prepared.claim().tenantId()) || !Set.of("TLS","STARTTLS").contains(profile.getTlsMode()))
                return MailWorkService.Outcome.FAILED;
            var address=network.authorize(profile.getHost(),profile.getPort());
            if(expired.get())return MailWorkService.Outcome.RETRYABLE;
            factory=new PinnedSmtpSocketFactory(address,profile.getPort());sockets.set(factory);
            if(expired.get()){factory.close();return MailWorkService.Outcome.RETRYABLE;}
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
            if(expired.get())return MailWorkService.Outcome.RETRYABLE;
            started.set(true);transport.sendMessage(message,message.getAllRecipients());return MailWorkService.Outcome.ACCEPTED;
        }catch(SmtpNetworkPolicy.PolicyDenied denied){return MailWorkService.Outcome.FAILED;}
        catch(AuthenticationFailedException invalid){return MailWorkService.Outcome.FAILED;}
        catch(Exception failure){return classify(failure,started.get());}
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
    @PreDestroy public void close(){executor.shutdownNow();}
}
