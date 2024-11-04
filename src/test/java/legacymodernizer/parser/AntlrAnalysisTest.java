package legacymodernizer.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import legacymodernizer.parser.controller.FileUploadController;

@SpringBootTest
public class AntlrAnalysisTest {
    
    @Autowired
    private FileUploadController fileUploadController;
    
    private static final String ANALYSIS_DIR = new File(System.getProperty("user.dir")).getParent() + File.separator + "analysis";
    private static final String PLSQL_DIR = new File(System.getProperty("user.dir")).getParent() + File.separator + "src";
    
    @BeforeEach
    void setUp() throws Exception {
        // analysis 폴더 내용 삭제
        File analysisDir = new File(ANALYSIS_DIR);
        if (analysisDir.exists()) {
            for (File file : analysisDir.listFiles()) {
                file.delete();
            }
        }
        System.out.println("Analysis 디렉토리 정리 완료");
    }
    
    @Test
    void testAnalysisWithExistingFiles() throws Exception {
        // src 폴더의 SQL 파일 목록 가져오기
        File srcDir = new File(PLSQL_DIR);
        File[] sqlFiles = srcDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".sql"));
        
        assertNotNull(sqlFiles, "SQL 파일을 찾을 수 없습니다");
        assertTrue(sqlFiles.length > 0, "분석할 SQL 파일이 없습니다");
        
        // 분석 요청 데이터 준비
        List<Map<String, String>> fileNames = new ArrayList<>();
        for (File sqlFile : sqlFiles) {
            Map<String, String> fileInfo = new HashMap<>();
            fileInfo.put("fileName", sqlFile.getName());
            fileNames.add(fileInfo);
        }
        
        Map<String, List<Map<String, String>>> request = new HashMap<>();
        request.put("fileNames", fileNames);
        
        // 분석 실행
        ResponseEntity<String> response = fileUploadController.analysisContext(request);
        
        // 결과 검증
        assertEquals(200, response.getStatusCode().value(), "분석이 실패했습니다");
        assertEquals("OK", response.getBody(), "분석 결과가 OK가 아닙니다");
        
        // 분석 결과 파일 확인
        for (File sqlFile : sqlFiles) {
            String baseFileName = sqlFile.getName().substring(0, sqlFile.getName().lastIndexOf('.'));
            Path resultPath = Paths.get(ANALYSIS_DIR, baseFileName + ".json");
            assertTrue(Files.exists(resultPath), 
                String.format("분석 결과 파일이 생성되지 않았습니다: %s", resultPath));
            
            String content = Files.readString(resultPath);
            assertFalse(content.isEmpty(), 
                String.format("분석 결과 파일이 비어있습니다: %s", resultPath));
            
            System.out.println("분석 완료: " + sqlFile.getName());
            System.out.println("결과 파일: " + resultPath);
            System.out.println("결과 내용: " + content);
            System.out.println("-------------------");
        }
    }
}