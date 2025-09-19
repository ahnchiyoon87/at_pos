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


    // SQL 객체(패키지/프로시저/함수/트리거)의 이름을 추출하기 위한 정규식 패턴
    // 특징:
    // - OR REPLACE 유무 허용, EDITIONABLE/NONEDITIONABLE 옵션 허용
    // - 스키마 접두사 유무 허용, 스키마/객체 모두 quoted 식별자 허용("NAME")
    // - TRIGGER 포함
    // - 캡처 그룹 "full"에 스키마.객체 또는 객체 단일명이 담김 (따옴표는 이후 제거)
    private static final Pattern SQL_OBJECT_PATTERN = Pattern.compile(
        "(?is)\\bCREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:EDITIONABLE\\s+|NONEDITIONABLE\\s+)?"
      + "(?:PACKAGE\\s+BODY|PACKAGE|PROCEDURE|FUNCTION|TRIGGER)\\s+"
      + "(?<full>(?:\"[^\"]+\"|[\\w$]+)(?:\\s*\\.\\s*(?:\"[^\"]+\"|[\\w$]+))?)"
    );


    // 파일 타입별 디렉토리 결정 메서드 추가
    public String getTargetDirectory(String sessionUUID, String fileName) {
        String subDir = PLSQL_DIR; // 기본값
        
        if (fileName != null) {
            String upperFileName = fileName.toUpperCase();
            if (upperFileName.contains("DDL")) subDir = DDL_DIR;
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
        if (upperFileName.contains("DDL")) return "DDL";
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
     * 테스트 샘플 처리를 위해 파일명 배열을 받아 해당 파일들의 정보를 조회하는 메서드
     * 
     * @param fileNames 조회할 파일명 배열
     * @param sessionUUID 세션 UUID
     * @return 조회된 파일 정보 목록 (fileName, objectName, fileContent, fileType)
     * @throws IOException 파일 처리 중 발생할 수 있는 예외
     */
    public List<Map<String, String>> processTestSample(List<String> fileNames, String sessionUUID) throws IOException {
        List<Map<String, String>> successFiles = new ArrayList<>();
        
        // src 디렉토리 경로 확인
        String srcDir = getTargetDirectory(sessionUUID, null); // PLSQL_DIR 사용
        // 분석 디렉토리 경로
        String analysisDir = getAnalysisDirectory(sessionUUID);
        
        File directory = new File(srcDir);
        File analysisDirectory = new File(analysisDir);
        
        if (!directory.exists()) {
            throw new IOException("소스 디렉토리를 찾을 수 없음: " + srcDir);
        }
        
        // 분석 디렉토리가 없으면 생성
        if (!analysisDirectory.exists()) {
            createDirectoryIfNotExists(analysisDir);
        }
        
        // 디렉토리 내 모든 파일 목록 가져오기 (탐색 성능 향상을 위해)
        File[] allFiles = directory.listFiles();
        if (allFiles == null || allFiles.length == 0) {
            System.out.println("디렉토리에 파일이 없음: " + srcDir);
            return successFiles;
        }
        
        // 각 파일에 대해 처리 수행
        for (String procedureName : fileNames) {
            // 파일 이름으로 변환 (확장자 추가)
            String sqlFileName = procedureName + ".sql";
            String baseFileName = procedureName;  // 확장자 없는 이름
            
            // 분석 결과 파일 경로 확인
            File analysisFile = new File(analysisDir, baseFileName + ".json");
            
            // 원본 SQL 파일 확인 - 여러 방법으로 파일 찾기
            File targetFile = new File(directory, sqlFileName);
            
            // 파일이 없으면 대소문자 구분 없이 디렉토리에서 찾기 시도
            if (!targetFile.exists()) {
                System.out.println("기본 이름으로 파일을 찾을 수 없음, 디렉토리 검색 시도: " + sqlFileName);
                
                boolean found = false;
                for (File file : allFiles) {
                    if (file.isFile() && file.getName().equalsIgnoreCase(sqlFileName)) {
                        targetFile = file;
                        found = true;
                        System.out.println("대소문자 무시 비교로 파일 찾음: " + file.getName());
                        break;
                    }
                }
                
                if (!found) {
                    System.out.println("파일을 찾을 수 없음: " + sqlFileName);
                    continue;
                }
            }
            
            // 파일 내용 읽기
            try {
                String fileContent = readFileContent(targetFile);
                String objectName = extractSqlObjectName(fileContent);
                String fileType = getFileType(targetFile.getName());
                
                // 분석 결과가 없는 경우만 분석 수행
                if (!analysisFile.exists()) {
                    System.out.println("분석 필요: " + targetFile.getName());
                    try {
                        // 분석 수행
                        parseAndSaveStructure(targetFile.getName(), sessionUUID);
                        System.out.println("분석 완료: " + targetFile.getName());
                    } catch (Exception e) {
                        System.out.println("분석 실패 (무시하고 계속 진행): " + targetFile.getName());
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("이미 분석된 파일: " + targetFile.getName() + " - 분석 건너뜀");
                }
                
                // 성공 목록에 추가 (분석 성공 여부와 관계없이)
                Map<String, String> fileData = new HashMap<>();
                fileData.put("objectName", objectName != null ? objectName : "");
                fileData.put("fileContent", fileContent);
                fileData.put("fileName", targetFile.getName());
                fileData.put("fileType", fileType);
                fileData.put("analysisExists", String.valueOf(analysisFile.exists()));
                
                successFiles.add(fileData);
                System.out.println("테스트 샘플 파일 처리 완료: " + targetFile.getName());
            } catch (Exception e) {
                System.out.println("파일 처리 중 오류 발생: " + targetFile.getName());
                e.printStackTrace();
            }
        }
        
        return successFiles;
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
     * 파일 시스템에 존재하는 SQL 파일들을 순회하면서,
     * 파일 내용에서 추출된 SQL 객체명이 입력받은 objectName과 일치하는 파일 정보를 반환합니다.
     *
     * @param sessionUUID 세션 UUID (파일 경로 구분용)
     * @param objectName  조회할 SQL 객체 이름 (대소문자 무시 비교)
     * @return 일치하는 SQL 객체 정보를 담은 파일 리스트 (fileName, objectName, fileContent, fileType)
     * @throws IOException 파일 읽기 실패 시 발생하는 예외
     */
    public List<Map<String, String>> retrieveFiles(String sessionUUID, String objectName) throws IOException {
        List<Map<String, String>> resultFiles = new ArrayList<>();
        
        // 조회 대상 서브 디렉토리 목록 (기존 업로드 시 사용한 디렉토리와 동일)
        String[] subDirs = { PLSQL_DIR, DDL_DIR, SEQ_DIR, ANALYSIS_DIR };

        for (String subDir : subDirs) {
            String targetDir = BASE_DIR + File.separator + sessionUUID + File.separator + subDir;
            File directory = new File(targetDir);
            if (directory.exists() && directory.isDirectory()) {
                // .sql 확장자를 가진 파일들만 조회 (필요에 따라 조건 수정 가능)
                File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".sql"));
                if (files != null) {
                    for (File file : files) {
                        String fileContent = readFileContent(file);
                        String extractedObject = extractSqlObjectName(fileContent);
                        if (extractedObject != null && extractedObject.equalsIgnoreCase(objectName)) {
                            Map<String, String> fileData = new HashMap<>();
                            fileData.put("fileName", file.getName());
                            fileData.put("objectName", extractedObject);
                            fileData.put("fileContent", fileContent);
                            fileData.put("fileType", getFileType(file.getName()));
                            resultFiles.add(fileData);
                            System.out.println("조회 성공 - 파일: " + file.getName() + ", 객체: " + extractedObject);
                        }
                    }
                }
            }
        }
        return resultFiles;
    }


    /**
     * PL/SQL 파일을 파싱하고 구조를 JSON 형식으로 저장
     * @param fileName 분석할 파일명
     * @param plsqlDir 파일이 저장된 디렉토리
     * @throws IOException 파일 처리 중 발생하는 예외
     */
    public void parseAndSaveStructure(String fileName, String sessionUUID) throws IOException {
        String analysisDir = getAnalysisDirectory(sessionUUID);
        String baseFileName = fileName.substring(0, fileName.lastIndexOf('.'));
        String outputPath = analysisDir + File.separator + baseFileName + ".json";

        // 분석 디렉토리가 없으면 생성
        createDirectoryIfNotExists(analysisDir);

        // 실제 존재하는 파일 경로를 탐색 (src, ddl, sequence 순)
        File candidate = null;
        String[] searchDirs = { getTargetDirectory(sessionUUID, null),
                                BASE_DIR + File.separator + sessionUUID + File.separator + DDL_DIR,
                                BASE_DIR + File.separator + sessionUUID + File.separator + SEQ_DIR };
        for (String dirPath : searchDirs) {
            File f = new File(dirPath, fileName);
            if (f.exists() && f.isFile()) {
                candidate = f;
                break;
            }
        }
        if (candidate == null) {
            // 마지막으로 대소문자 무시 검색 시도
            outer:
            for (String dirPath : searchDirs) {
                File dir = new File(dirPath);
                File[] files = dir.listFiles();
                if (files == null) continue;
                for (File f : files) {
                    if (f.isFile() && f.getName().equalsIgnoreCase(fileName)) {
                        candidate = f;
                        break outer;
                    }
                }
            }
        }
        if (candidate == null) {
            throw new IOException("분석 대상 파일을 찾을 수 없음: " + fileName);
        }

        try (InputStream in = new FileInputStream(candidate)) {

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
            String name = matcher.group("full");
            if (name == null) return null;
            name = name.replace("\"", "");
            name = name.replaceAll("\\s*\\.\\s*", ".");
            name = name.replaceAll("^.*\\.", ""); // 스키마 접두사 제거
            return name;
        }
        return null;
    }
}