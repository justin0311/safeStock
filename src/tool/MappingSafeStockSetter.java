package tool;

import java.util.Objects;

/**
 * 맵핑에서 안전재고 데이터를 업데이트 하는 정책 구현클래스
 */
@Component
public class MappingSafeStockSetter extends SafeStockSetter {

    @Autowired
    SafeStockRepository safeStockRepository;

    @Override
    public SafeStockSetterType getSafeStockSetterType() {
        return SafeStockSetterType.MAPPING;
    }

    @Override
    @SuppressWarnings(value = "unchecked")
    public Boolean work(SafeStock safeStockParam) {
        SafeStock safeStock = safeStockRepository.findOne(new SafeStockId(safeStockParam.getSkuSrl(), safeStockParam.getCenterCode()));
        safeStock = saveSafeStock(safeStock, safeStockParam);
        SafeStock result = safeStockRepository.save(safeStock);

        return Objects.nonNull(result);
    }

    private SafeStock saveSafeStock(SafeStock safeStock, SafeStock safeStockParam) {
        if (Objects.isNull(safeStock)) {
            safeStock = new SafeStock();
            safeStock.setSkuSrl(safeStockParam.getSkuSrl());
            safeStock.setCenterCode(safeStockParam.getCenterCode());
            safeStock.setSafeRate(safeStockParam.getSafeRate());
            safeStock.setSafeAmt(0L);
            safeStock.setRegId(safeStockParam.getChgId());
            safeStock.setRegDt(safeStockParam.getChgDt());
            safeStock.setChgId(safeStockParam.getChgId());
            safeStock.setChgDt(safeStockParam.getChgDt());
        }
        safeStock.setSafeRate(safeStockParam.getSafeRate());
        safeStock.setChgId(safeStockParam.getChgId());
        safeStock.setChgDt(safeStockParam.getChgDt());

        return safeStock;
    }
}
