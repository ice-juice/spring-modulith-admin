// Explicit TEST directory data in the security runner's disposable schema only.
// No production migration, domain-verification bypass, external provider or credential output.
export function securityTenantFixture(scope) {
  if (scope?.suite !== 'security' || !/^sma_security_e2e_\d{13}$/.test(scope?.schema || '')) {
    throw new Error('Tenant fixture requires a generated security schema')
  }
  const db = '`' + scope.schema + '`'
  return {
    host: 'tenant-security.test', username: 'tenant_security_admin',
    preflight: `SELECT (SELECT COUNT(*) FROM ${db}.tenant_tenant WHERE id=2)
      +(SELECT COUNT(*) FROM ${db}.iam_user WHERE id=22001)
      +(SELECT COUNT(*) FROM ${db}.tenant_domain WHERE id=22002 OR hostname='tenant-security.test')
      +(SELECT COUNT(*) FROM ${db}.iam_permission WHERE id BETWEEN 23001 AND 33000)
      +(SELECT COUNT(*) FROM ${db}.config_parameter WHERE id=22003);`,
    seed: `
      INSERT INTO ${db}.tenant_tenant (id,code,name,status,security_generation,created_at,created_by,updated_at,updated_by,deleted)
        VALUES (2,'security-fixture','Security TEST tenant','ACTIVE',1,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0);
      INSERT INTO ${db}.tenant_domain (id,owner_tenant_id,hostname,portal,status,primary_domain,verification_status,tls_status,provenance,dns_status,verified_at,tls_valid_until,created_at,created_by,updated_at,updated_by,deleted)
        VALUES (22002,2,'tenant-security.test','admin','ACTIVE',0,'VERIFIED','READY','TEST','READY',UTC_TIMESTAMP(3),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY),UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0);
      INSERT INTO ${db}.iam_user (id,tenant_id,username,name,password_hash,portal,enabled,super_admin,locale,time_zone,password_changed_at,credential_version,created_at,created_by,updated_at,updated_by,deleted)
        SELECT 22001,2,'tenant_security_admin','Security TEST administrator',password_hash,'admin',1,1,'zh-CN','UTC',password_changed_at,0,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0
        FROM ${db}.iam_user WHERE tenant_id=1 AND username='admin' AND portal='admin' AND deleted=0;
      CREATE TABLE ${db}.e2e_permission_map AS SELECT id AS source_id,23000+ROW_NUMBER() OVER (ORDER BY id) AS target_id
        FROM ${db}.iam_permission WHERE tenant_id=1 AND portal='admin' AND deleted=0;
      INSERT INTO ${db}.iam_permission (id,tenant_id,parent_id,portal,permission_key,name,name_key,type,path,icon,open_mode,sort,enabled,hidden,builtin,super_admin_default,created_at,created_by,updated_at,updated_by,deleted)
        SELECT m.target_id,2,COALESCE(parent.target_id,0),p.portal,p.permission_key,p.name,p.name_key,p.type,p.path,p.icon,p.open_mode,p.sort,p.enabled,p.hidden,p.builtin,p.super_admin_default,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0
        FROM ${db}.iam_permission p JOIN ${db}.e2e_permission_map m ON m.source_id=p.id
        LEFT JOIN ${db}.e2e_permission_map parent ON parent.source_id=p.parent_id WHERE p.tenant_id=1 AND p.deleted=0;
      DROP TABLE ${db}.e2e_permission_map;
      INSERT INTO ${db}.config_parameter (id,tenant_id,param_key,group_key,name,name_key,value_type,param_value,builtin,public_readable,secret,sort,created_at,created_by,updated_at,updated_by,deleted,definition_contract,value_overridden,presentation_overridden)
        SELECT 22003,2,param_key,group_key,name,name_key,value_type,'false',builtin,public_readable,secret,sort,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0,definition_contract,1,0
        FROM ${db}.config_parameter WHERE tenant_id=1 AND param_key='login.captchaEnabled' AND deleted=0;
    `,
    verified: `SELECT IF(
      (SELECT COUNT(*) FROM ${db}.iam_user WHERE id=22001 AND tenant_id=2 AND super_admin=1)=1
      AND (SELECT COUNT(*) FROM ${db}.tenant_domain WHERE id=22002 AND owner_tenant_id=2 AND provenance='TEST')=1
      AND (SELECT COUNT(*) FROM ${db}.config_parameter WHERE id=22003 AND tenant_id=2 AND param_value='false')=1
      AND (SELECT COUNT(*) FROM ${db}.iam_permission WHERE tenant_id=2 AND deleted=0)=(SELECT COUNT(*) FROM ${db}.iam_permission WHERE tenant_id=1 AND deleted=0),1,0);`,
  }
}
