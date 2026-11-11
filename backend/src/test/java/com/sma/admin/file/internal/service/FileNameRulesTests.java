package com.sma.admin.file.internal.service;

import static org.assertj.core.api.Assertions.*;
import com.sma.admin.common.BizException;
import com.sma.admin.file.FileFormat;
import org.junit.jupiter.api.Test;

class FileNameRulesTests {
    @Test void displayNamesRejectExecutionConfusionPathsDevicesAndHiddenCharacters(){
        assertThat(FileNameRules.normalize("旅行照片.JPG",FileFormat.JPEG)).isEqualTo("旅行照片.JPG");
        for(String name:new String[]{"../a.png","C:\\a.png","a.php.png","a.PNG.exe","NUL.png","folder/a.png","a.png:script","a.png."," a.png","a\u202Egnp.png",".htaccess","a..png","a.svg","a.php5.png"})
            assertThatThrownBy(()->FileNameRules.normalize(name,FileFormat.PNG)).as(name).isInstanceOf(BizException.class);
        assertThatThrownBy(()->FileNameRules.normalize("photo.jpg",FileFormat.PNG)).isInstanceOf(BizException.class);
    }
}
