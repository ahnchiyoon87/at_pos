package legacymodernizer.parser.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import legacymodernizer.parser.antlr.CaseChangingCharStream;
import legacymodernizer.parser.antlr.CustomPlSqlListener;
import legacymodernizer.parser.antlr.plsql.PlSqlLexer;
import legacymodernizer.parser.antlr.plsql.PlSqlParser;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PlSqlFileParserService {

    // 기존 상수들을 서비스로 이동
    private static final String BASE_DIR = System.getenv("DOCKER_COMPOSE_CONTEXT") != null ?
            System.getenv("DOCKER_COMPOSE_CONTEXT") :
            new File(System.getProperty("user.dir")).getParent() + File.separator + "data";


    // 각 디렉토리 경로 상수
    private static final String PLSQL_DIR = "src";
    private static final String DDL_DIR = "ddl";
    private static final String SEQ_DIR = "sequence";
    private static final String ANALYSIS_DIR = "analysis";


    // SQL 객체(패키지, 프로시저, 함수 등)의 이름을 추출하기 위한 정규식 패턴
    private static final Pattern SQL_OBJECT_PATTERN = Pattern.compile(
        "CREATE\\s+OR\\s+REPLACE\\s+(?:PACKAGE|PACKAGE BODY|PROCEDURE|FUNCTION)\\s+([\\w$]+)", 
        Pattern.CASE_INSENSITIVE
    );


    // 파일 타입별 디렉토리 결정 메서드 추가
    public String getTargetDirectory(String sessionUUID, String fileName) {
        String subDir = PLSQL_DIR; // 기본값
        
        if (fileName != null) {
            String upperFileName = fileName.toUpperCase();
            if (upperFileName.contains("TPJ")) subDir = DDL_DIR;
            if (upperFileName.contains("SEQ")) subDir = SEQ_DIR;
        }
        
        return BASE_DIR + File.separator + sessionUUID + File.separator + subDir;
    }


    // 분석 디렉토리 경로 반환 메서드 추가
    public String getAnalysisDirectory(String sessionUUID) {
        return BASE_DIR + File.separator + sessionUUID + File.separator + ANALYSIS_DIR;
    }

    // 파일 타입 추출 메서드 추가
    private String getFileType(String fileName) {
        if (fileName == null) return "PLSQL";
        
        String upperFileName = fileName.toUpperCase();
        if (upperFileName.contains("TPJ")) return "DDL";
        if (upperFileName.contains("SEQ")) return "SEQ";
        return "PLSQL";
    }

    /**
     * MultipartFile을 받아 파일 시스템에 저장하고 관련 정보를 반환
     * @param file 업로드된 MultipartFile 객체
     * @param sessionUUID 세션 UUID
     * @return 파일명, 파일내용, 객체명을 포함한 Map
     * @throws IOException 파일 처리 중 발생하는 예외
     */
    public Map<String, String> saveFile(MultipartFile file, String sessionUUID) throws IOException {
        String fileName = file.getOriginalFilename();
        String targetDir = getTargetDirectory(sessionUUID, fileName);
        File directory = new File(targetDir);

        // 저장 디렉토리가 없으면 생성
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("디렉토리 생성 실패: " + targetDir);
        }

        // 파일 저장 및 내용 분석
        File outputFile = new File(directory, fileName);

        // 파일이 이미 존재하지 않는 경우에만 저장
        if (!outputFile.exists()) {
            file.transferTo(outputFile);
            System.out.println("새 파일 저장됨: " + fileName);
        } else {
            System.out.println("파일이 이미 존재하여 저장하지 않음: " + fileName);
        }

        String fileContent = readFileContent(outputFile);                  
        String objectName = extractSqlObjectName(fileContent); 
        String fileType = getFileType(fileName);
        
        Map<String, String> result = new HashMap<>();
        result.put("fileName", fileName != null ? fileName : "");
        result.put("fileContent", fileContent != null ? fileContent : "");
        result.put("objectName", objectName != null ? objectName : "");
        result.put("fileType", fileType != null ? fileType : "UNKNOWN");
    
        System.out.println("추출된 SQL 객체 이름: " + objectName);
        return result;
    }

    
    /**
     * 파일의 내용을 다양한 인코딩으로 시도하여 읽어옴
     * @param file 읽을 파일 객체
     * @return 파일의 내용 문자열
     * @throws IOException 파일 읽기 실패시 발생
     */
    public String readFileContent(File file) throws IOException {
        try {
            // UTF-8로 먼저 시도
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            try {
                // UTF-8 실패시 EUC-KR 시도
                return Files.readString(file.toPath(), Charset.forName("EUC-KR"));
            } catch (Exception e2) {
                // EUC-KR 실패시 MS949 시도
                return Files.readString(file.toPath(), Charset.forName("MS949"));
            }
        }
    }


    /**
     * PL/SQL 파일을 파싱하고 구조를 JSON 형식으로 저장
     * @param fileName 분석할 파일명
     * @param plsqlDir 파일이 저장된 디렉토리
     * @throws IOException 파일 처리 중 발생하는 예외
     */
    public void parseAndSaveStructure(String fileName, String sessionUUID) throws IOException {
        String plsqlDir = getTargetDirectory(sessionUUID, fileName);
        String analysisDir = getAnalysisDirectory(sessionUUID);
        String baseFileName = fileName.substring(0, fileName.lastIndexOf('.'));
        String outputPath = analysisDir + File.separator + baseFileName + ".json";

        // 분석 디렉토리가 없으면 생성
        createDirectoryIfNotExists(analysisDir);


        try (InputStream in = new FileInputStream(new File(plsqlDir, fileName))) {

            // ANTLR 파서 설정
            CharStream s = CharStreams.fromStream(in);
            CaseChangingCharStream upper = new CaseChangingCharStream(s, true);
            PlSqlLexer lexer = new PlSqlLexer(upper);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            
            // 구문 분석
            PlSqlParser parser = new PlSqlParser(tokens);
            ParserRuleContext tree = parser.sql_script();
            
            // 리스너를 통한 구문 트리 순회
            CustomPlSqlListener listener = new CustomPlSqlListener(tokens);
            new ParseTreeWalker().walk(listener, tree);
            
            // 분석 결과를 JSON 파일로 저장
            File analysisFile = new File(outputPath);
            try (FileWriter file = new FileWriter(analysisFile)) {
                file.write(listener.getRoot().toJson());
            }
        }
    }


    public List<Map<String, String>> analyzeAllFilesInDirectory(String sessionUUID) throws IOException {
        String plsqlDir = getTargetDirectory(sessionUUID, null);
        String analysisDir = getAnalysisDirectory(sessionUUID);
        
        // PLSQL 디렉토리 확인
        File plsqlDirectory = new File(plsqlDir);
        if (!plsqlDirectory.exists() || !plsqlDirectory.isDirectory()) {
            throw new IOException("PLSQL 디렉토리를 찾을 수 없음: " + plsqlDir);
        }

        // 분석 디렉토리 생성
        createDirectoryIfNotExists(analysisDir);
        
        // PLSQL 파일 목록 가져오기
        File[] allFiles = plsqlDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".sql"));
        if (allFiles == null || allFiles.length == 0) {
            throw new IOException("파일을 찾을 수 없음: " + plsqlDir);
        }

        System.out.println("분석 시작: 총 " + allFiles.length + "개의 파일");
        List<Map<String, String>> successFiles = new ArrayList<>();

        // 각 파일별 분석 수행
        for (File sqlFile : allFiles) {
            String fileName = sqlFile.getName();
            String fileContent = readFileContent(sqlFile);
            String objectName = extractSqlObjectName(fileContent);
            
            // 분석 수행
            parseAndSaveStructure(fileName, sessionUUID);
            
            Map<String, String> fileData = new HashMap<>();
            fileData.put("fileName", fileName);
            fileData.put("objectName", objectName);
            fileData.put("fileContent", fileContent);
            successFiles.add(fileData);
            
            System.out.println("파일 분석 완료: " + fileName);
        }
        
        return successFiles;
    }
    
    private void createDirectoryIfNotExists(String path) throws IOException {
        Path dirPath = Paths.get(path);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
            log.info("디렉토리 생성됨: {}", path);
        }
    }

    
    /**
     * SQL 내용에서 객체 이름을 추출
     * @param sqlContent SQL 파일 내용
     * @return 추출된 객체 이름, 매칭되지 않으면 null
     */
    public String extractSqlObjectName(String sqlContent) {
        Matcher matcher = SQL_OBJECT_PATTERN.matcher(sqlContent);
        if (matcher.find()) {
            return matcher.group(1);  // 첫 번째 매칭된 객체 이름만 반환
        }
        return null;
    }
}