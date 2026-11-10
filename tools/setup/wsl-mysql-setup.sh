#!/usr/bin/env bash
# Runs inside WSL Ubuntu as root: install MySQL 8 and import the JBolt schema.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# JBolt source project: sibling of the repository by default; override with SMA_JBOLT_HOME
JBOLT_SQL_DIR="${SMA_JBOLT_HOME:-$SCRIPT_DIR/../../../jbolt_ai_platform_jfinal_customer}/readme"
DB_NAME="jboltai"
DB_PASS="root"   # matches JBolt default dbconfig/mysql/config.properties (local development only)
APP_DB="springboot_modulith_admin"          # new Spring Modulith project database
APP_USER="springboot_modulith_admin"
APP_PASS="sma_dev"    # local development only, see backend/src/main/resources/application.yml

export DEBIAN_FRONTEND=noninteractive
if ! command -v mysqld >/dev/null 2>&1; then
  apt-get update -y
  apt-get install -y mysql-server
fi

cat > /etc/mysql/mysql.conf.d/zz-dev.cnf <<'EOF'
[mysqld]
bind-address = 0.0.0.0
character-set-server = utf8mb4
collation-server = utf8mb4_general_ci
default-time-zone = '+08:00'
sql_mode = STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION
max_connections = 300
EOF

service mysql restart || systemctl restart mysql

mysql --protocol=socket -uroot <<SQL || mysql -uroot -p"$DB_PASS" -e "select 1" >/dev/null
ALTER USER 'root'@'localhost' IDENTIFIED WITH caching_sha2_password BY '$DB_PASS';
CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED WITH caching_sha2_password BY '$DB_PASS';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
SQL

MYSQL=(mysql -uroot -p"$DB_PASS" --default-character-set=utf8mb4)
"${MYSQL[@]}" -e "CREATE DATABASE IF NOT EXISTS $DB_NAME DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
TABLES=$("${MYSQL[@]}" -N -e "select count(*) from information_schema.tables where table_schema='$DB_NAME'")
if [ "$TABLES" = "0" ]; then
  "${MYSQL[@]}" "$DB_NAME" < "$JBOLT_SQL_DIR/jbolt_mysql_id_long.sql"
  "${MYSQL[@]}" "$DB_NAME" < "$JBOLT_SQL_DIR/jbolt_ai.sql"
fi
"${MYSQL[@]}" -N -e "select version(); select count(*) as tables_in_jboltai from information_schema.tables where table_schema='$DB_NAME';"

"${MYSQL[@]}" <<SQL
CREATE DATABASE IF NOT EXISTS $APP_DB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '$APP_USER'@'%' IDENTIFIED BY '$APP_PASS';
GRANT ALL PRIVILEGES ON $APP_DB.* TO '$APP_USER'@'%';
FLUSH PRIVILEGES;
SQL
echo "app database $APP_DB ready"

