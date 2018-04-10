package tool;


import com.tmoncorp.wms.module.safestock.domain.SafeStock;

/**
 * 안전재고 수량/율을 관리하는 추상화 클래스
 */
public abstract class SafeStockSetter {

    public abstract SafeStockSetterType getSafeStockSetterType();

    public abstract <T> T work(SafeStock safeStockParam);
}