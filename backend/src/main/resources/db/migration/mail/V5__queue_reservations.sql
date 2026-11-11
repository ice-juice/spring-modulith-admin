ALTER TABLE mail_profile ADD pending_deliveries INT NOT NULL DEFAULT 0;
UPDATE mail_profile p SET pending_deliveries=(
 SELECT COUNT(*) FROM mail_delivery d WHERE d.tenant_id=p.tenant_id AND d.profile_id=p.id
 AND d.deleted=0 AND d.state IN ('QUEUED','CLAIMED','SENDING')
);
