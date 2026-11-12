package com.sma.admin.common.deployment;

import java.util.*;
import java.util.concurrent.*;

/** Bounded, identity-free tracking of real task execution, with no executor or context propagation of its own.
 * The caller must already own its parent request/work and its executor. Submit before handing work off.
 * Future cancellation completes the Future immediately; this tracker retains started work until run exits.
 * close() stops submissions and requests cancellation, never waits or pretends running work has stopped.
 * An executor's shutdownNow() must be paired with close() so removed queued tasks are also cancelled.
 */
public final class DeploymentTaskTracker implements AutoCloseable {
    private final int maximum;
    private final Set<Task<?>> tasks=new HashSet<>();
    private int queued,running;
    private boolean closed;

    public DeploymentTaskTracker(int maximum){
        if(maximum<1||maximum>100_000)throw new IllegalArgumentException("Invalid activity capacity");
        this.maximum=maximum;
    }
    public synchronized DeploymentActivity.Snapshot snapshot(){return new DeploymentActivity.Snapshot(queued,running);}

    /** Refuse future submissions without cancelling any previously accepted task. */
    public synchronized void stopAccepting(){closed=true;}

    public <T> Future<T> submit(Executor executor,Callable<T> action){
        Objects.requireNonNull(executor);Objects.requireNonNull(action);
        Task<T> task;
        synchronized(this){
            if(closed||tasks.size()>=maximum)throw new RejectedExecutionException("Deployment activity unavailable");
            task=new Task<>(action);tasks.add(task);queued++;
        }
        try{executor.execute(task);return task;}
        catch(RuntimeException|Error failure){task.cancel(false);throw failure;}
    }
    @Override public void close(){
        List<Task<?>> pending;
        synchronized(this){closed=true;pending=List.copyOf(tasks);}
        // Do not hold our monitor while an interrupt/cancellation invokes another component's code.
        pending.forEach(task->task.cancel(true));
    }

    private final class Task<T> extends FutureTask<T> {
        private boolean started,released;
        Task(Callable<T> action){super(action);}
        @Override public void run(){
            synchronized(DeploymentTaskTracker.this){
                if(started||released)return;
                started=true;queued--;running++;
            }
            try{super.run();}
            finally{synchronized(DeploymentTaskTracker.this){release();}}
        }
        @Override protected void done(){
            synchronized(DeploymentTaskTracker.this){if(!started)release();}
        }
        /** Caller holds the tracker monitor. */
        private void release(){
            if(released)return;released=true;tasks.remove(this);
            if(started)running--;else queued--;
        }
    }
}
