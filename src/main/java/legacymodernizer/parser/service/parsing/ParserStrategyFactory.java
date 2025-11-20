package legacymodernizer.parser.service.parsing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * DBMS별 파싱 전략을 제공하는 팩토리 클래스
 * - 전략 패턴의 Context 역할
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParserStrategyFactory {
    
    private final PlSqlParserStrategy plSqlParserStrategy;
    private final PostgreSqlParserStrategy postgreSqlParserStrategy;
    private final DmlPostgreSqlParserStrategy dmlPostgreSqlParserStrategy;
    
    private Map<String, DbmsParserStrategy> strategyMap;
    
    /**
     * DBMS 타입에 따른 파싱 전략 반환
     * @param dbmsType DBMS 타입 ("oracle", "postgresql" 등)
     * @return 해당 DBMS의 파싱 전략
     * @throws IllegalArgumentException 지원하지 않는 DBMS 타입인 경우
     */
    public DbmsParserStrategy getStrategy(String dbmsType) {
        if (strategyMap == null) {
            initStrategyMap();
        }
        
        if (dbmsType == null || dbmsType.trim().isEmpty()) {
            log.warn("DBMS 타입이 지정되지 않았습니다. 기본값(oracle)을 사용합니다.");
            return plSqlParserStrategy;
        }
        
        String normalizedType = normalizeDbmsType(dbmsType);
        DbmsParserStrategy strategy = strategyMap.get(normalizedType);
        
        if (strategy == null) {
            log.error("지원하지 않는 DBMS 타입입니다: {}", dbmsType);
            throw new IllegalArgumentException(
                "지원하지 않는 DBMS 타입입니다: " + dbmsType + 
                ". 지원 가능한 타입: " + String.join(", ", strategyMap.keySet())
            );
        }
        
        log.debug("DBMS 파싱 전략 선택: {} → {}", dbmsType, strategy.getClass().getSimpleName());
        return strategy;
    }
    
    /**
     * 전략 맵 초기화
     */
    private void initStrategyMap() {
        strategyMap = new HashMap<>();
        strategyMap.put("oracle", plSqlParserStrategy);
        strategyMap.put("plsql", plSqlParserStrategy);
        strategyMap.put("postgresql", postgreSqlParserStrategy);
        strategyMap.put("postgres", postgreSqlParserStrategy);
        strategyMap.put("pg", postgreSqlParserStrategy);
        strategyMap.put("dml_postgresql", dmlPostgreSqlParserStrategy);
        strategyMap.put("dml_postgres", dmlPostgreSqlParserStrategy);
        strategyMap.put("dml_pg", dmlPostgreSqlParserStrategy);
    }
    
    /**
     * DBMS 타입 정규화 (소문자 변환 및 공백 제거)
     * @param dbmsType 원본 DBMS 타입
     * @return 정규화된 DBMS 타입
     */
    private String normalizeDbmsType(String dbmsType) {
        return dbmsType.trim().toLowerCase();
    }
    
    /**
     * 지원하는 DBMS 타입 목록 반환
     * @return 지원 가능한 DBMS 타입 목록
     */
    public List<String> getSupportedDbmsTypes() {
        if (strategyMap == null) {
            initStrategyMap();
        }
        return List.copyOf(strategyMap.keySet());
    }
}

