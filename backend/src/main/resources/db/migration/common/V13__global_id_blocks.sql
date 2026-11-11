-- Initial cutover requires all legacy ID writers to be stopped.
-- This migration reads only indexed numeric primary-ID maxima, never business payloads.
CREATE TABLE common_id_block (
 id BIGINT NOT NULL PRIMARY KEY, next_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_common_id_block CHECK(id=1 AND next_id>0 AND deleted=0)
);

DELIMITER $$
CREATE PROCEDURE common_p08_seed_ids_v13()
BEGIN
 DECLARE finished BOOLEAN DEFAULT FALSE;
 DECLARE candidate VARCHAR(64);
 DECLARE largest DECIMAL(20,0) DEFAULT 0;
 DECLARE tables_with_ids CURSOR FOR
  SELECT c.TABLE_NAME FROM information_schema.COLUMNS c
  JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME
  WHERE c.TABLE_SCHEMA=DATABASE() AND c.COLUMN_NAME='id' AND c.COLUMN_KEY='PRI'
   AND c.DATA_TYPE IN ('bigint','int','mediumint','smallint','tinyint') AND t.TABLE_TYPE='BASE TABLE';
 DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished=TRUE;
 IF EXISTS (
  SELECT 1 FROM information_schema.COLUMNS c
  WHERE c.TABLE_SCHEMA=DATABASE() AND c.COLUMN_NAME='id' AND c.COLUMN_KEY='PRI'
   AND c.DATA_TYPE IN ('bigint','int','mediumint','smallint','tinyint')
   AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s
     WHERE s.TABLE_SCHEMA=c.TABLE_SCHEMA AND s.TABLE_NAME=c.TABLE_NAME AND s.COLUMN_NAME='id' AND s.SEQ_IN_INDEX=1)
 ) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Numeric primary IDs need a leading index for bounded ID initialization';
 END IF;
 OPEN tables_with_ids;
 scan_ids: LOOP
  FETCH tables_with_ids INTO candidate;
  IF finished THEN LEAVE scan_ids; END IF;
  SET @common_p08_max_id=NULL;
  SET @common_p08_id_sql=CONCAT('SELECT MAX(`id`) INTO @common_p08_max_id FROM `',REPLACE(candidate,'`','``'),'`');
  PREPARE common_p08_id_statement FROM @common_p08_id_sql;
  EXECUTE common_p08_id_statement;
  DEALLOCATE PREPARE common_p08_id_statement;
  SET largest=GREATEST(largest,COALESCE(@common_p08_max_id,0));
 END LOOP;
 CLOSE tables_with_ids;
 IF largest>=9223372036854774783 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Insufficient signed Long ID space';
 END IF;
 INSERT INTO common_id_block(id,next_id,created_at,created_by,updated_at,updated_by,deleted)
 VALUES(1,GREATEST(largest+1,4503599627370496),UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0);
 SET @common_p08_max_id=NULL;
 SET @common_p08_id_sql=NULL;
END$$
DELIMITER ;
CALL common_p08_seed_ids_v13();
DROP PROCEDURE common_p08_seed_ids_v13;
