package com.sma.admin.extension.internal.service;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.sma.admin.common.BizException;
import com.sma.admin.common.extension.ExtensionWorkReconciler;
import com.sma.admin.extension.ExtensionErrorCode;
import com.sma.admin.extension.ExtensionWork;
import com.sma.extension.protocol.*;

@Service
public class ExtensionReconciliationService {
    private final ExtensionWorkJournal journal;
    private final Map<String,ExtensionWorkReconciler> reconcilers;
    public ExtensionReconciliationService(ExtensionWorkJournal journal,List<ExtensionWorkReconciler> adapters) {
        this.journal=journal;reconcilers=adapters.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(a -> a.pluginId()+":"+a.entryId(),a -> a));
    }
    public ExtensionWork reconcile(long id) {
        try {
            var work=journal.get(id);if(!java.util.Set.of("RUNNING","UNKNOWN").contains(work.state()))return journal.view(work);
            var adapter=reconcilers.get(work.plugin()+":"+work.entry());Checks.require(adapter!=null,"work-reconciler-unavailable");
            var caller=WorkCaller.current();var selection=work.selection();
            var context=new ExtensionWorkReconciler.Work(Long.toString(id),caller.tenantId(),caller.subject(),caller.portal(),work.deployment(),work.plugin(),work.entry(),selection.releaseRevision(),selection.bundleId(),work.inputHash(),work.claimId());
            var result=adapter.reconcile(context);Checks.require(result!=null,"reconciliation-result-required");
            var resolved=journal.reconcile(id,work.claimId(),result);
            org.slf4j.LoggerFactory.getLogger("sma.extension.reconciliation").info("work={} tenant={} state={} evidence={}",id,caller.tenantId(),resolved.state(),result.evidenceSha256());
            return journal.view(resolved);
        } catch(ProtocolException failure){throw new BizException(ExtensionErrorCode.REJECTED,failure.code());}
    }
}
