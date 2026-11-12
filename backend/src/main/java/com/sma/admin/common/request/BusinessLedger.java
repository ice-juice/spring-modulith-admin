package com.sma.admin.common.request;

/** A business storage group selects one pinned resource before starting local work. */
public interface BusinessLedger {
    Session open(String storageGroup);

    interface Session extends AutoCloseable {
        /** Host-pinned physical identity. Legacy providers must explicitly implement this before using business caching. */
        default com.sma.admin.common.persistence.DataSourceIdentity resourceIdentity() {
            throw new UnsupportedOperationException("Business resource identity is required");
        }
        /** The execution row, mutex and business write must use this session's one database. */
        <T> RequestExecution.Outcome<T> executeLocal(RequestCommand command, RequestExecution.Work<T> work);

        RequestReceipt receipt(String operationId);

        RequestReceipt receiptByKey(String key);

        /** Record a DATA region revision in the same writable business transaction. */
        void invalidate(String region);

        /** The latest committed revision from this business resource, read outside a transaction. */
        long authoritativeRevision(String region);

        /** Store a bounded event intent with the business write. Reusing a key must retain its original bytes. */
        long enqueue(String eventType, String eventKey, byte[] payload);

        /** Run a read-only business Mapper call against the pinned physical resource. */
        <T> T query(java.util.function.Supplier<T> read);

        /** Release the resource revision lease after the operation or receipt query. */
        @Override void close();
    }
}
