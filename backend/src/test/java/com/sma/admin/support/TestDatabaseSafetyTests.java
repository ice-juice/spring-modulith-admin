package com.sma.admin.support;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class TestDatabaseSafetyTests {
 @Test void cleanRejectsBusinessAndSystemDatabasesBeforeOpeningAConnection(){
  assertThatCode(()->TestDatabase.ensureSafeDatabase("jdbc:mysql://127.0.0.1:3306/springboot_modulith_admin_test?x=y")).doesNotThrowAnyException();
  assertThatThrownBy(()->TestDatabase.ensureSafeDatabase("jdbc:mysql://127.0.0.1:3306/springboot_modulith_admin")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->TestDatabase.ensureSafeDatabase("jdbc:mysql://127.0.0.1:3306/mysql")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->TestDatabase.ensureSafeDatabase("jdbc:h2:mem:business")).isInstanceOf(IllegalArgumentException.class);
 }
}
