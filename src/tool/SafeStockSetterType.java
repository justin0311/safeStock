package tool;

/**
 * 안전재고 수량/율 업데이트 관련 작업의 종류를 나타냅니다.
 */
public enum SafeStockSetterType {
    POMS,   // POMS에서 안전재고 Upsert
    RECEIPT,    // 입고 배치에서 안전재고 Upsert
    MAPPING,    // 맵핑에서 안전재고 Upsert
}
