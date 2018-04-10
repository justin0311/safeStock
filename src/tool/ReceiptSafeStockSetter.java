package tool;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 입고 배치에서 안전재고 업데이트 하는 정책 구현클래스
 */
@Component
public class ReceiptSafeStockSetter extends SafeStockSetter {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final String STOCKSYNC_IN_JOB = "StocksyncInJob";

    @Autowired
    SafeStockRepository safeStockRepository;
    @Autowired
    SafeStockLogRepository safeStockLogRepository;

    @Override
    public SafeStockSetterType getSafeStockSetterType() {
        return SafeStockSetterType.RECEIPT;
    }

    @Override
    @SuppressWarnings(value = "unchecked")
    public <T> T work(SafeStock safeStockParam) {
        Integer skuSrl = safeStockParam.getSkuSrl();
        String centerCode = safeStockParam.getCenterCode();
        Long skuQty = safeStockParam.getSafeAmt();
        Date date = safeStockParam.getChgDt();
        String logSender = safeStockParam.getChgId();

        SafeStock safeStock = safeStockRepository.findOne(new SafeStockId(skuSrl, centerCode));

        if (Objects.isNull(safeStock)) {
            List<SafeStock> existSafeStockList = safeStockRepository.findBySkuSrl(skuSrl);
            if (CollectionUtils.isEmpty(existSafeStockList)) {  //안전재고 SKU 아님
                logger.error("안전재고에 등록되지 않은 SKU가 입고 되었습니다. skuSrl[{}]", skuSrl);

            } else {    // skuSrl & centerCode 컴보가 없을때는 데이터 insert
                // skuSrl 있음. 센터코드는 없음
                SafeStock newSafeStock = new SafeStock();
                newSafeStock.setSkuSrl(skuSrl);
                newSafeStock.setCenterCode(centerCode);
                newSafeStock.setSafeRate(0L);
                newSafeStock.setSafeAmt(0L);
                newSafeStock.setRegId(STOCKSYNC_IN_JOB);
                newSafeStock.setRegDt(date);
                newSafeStock.setChgId(STOCKSYNC_IN_JOB);
                newSafeStock.setChgDt(date);

                safeStockRepository.save(newSafeStock);
            }

        } else {    // skuSrl && centerCode 컴보가 있을때 (newSafeAmt = safeRate * skuQty)
            Long safeRate = (Objects.isNull(safeStock.getSafeRate()) || safeStock.getSafeRate() < 0) ? 0 : safeStock.getSafeRate();
            Long newSafeStockAmt = (long) Math.round(skuQty * safeRate / 100);

            if (newSafeStockAmt != safeStock.getSafeAmt()) {
                saveSafeStockLog(safeStockParam, newSafeStockAmt, safeRate, logSender);
                saveSafeStock(safeStock, newSafeStockAmt, date, STOCKSYNC_IN_JOB);
            }
        }
        return null;
    }

    private void saveSafeStockLog(SafeStock safeStockParam, Long newSafeStockAmt, Long safeRate, String logSender) {
        SafeStockLog safeStockLog = new SafeStockLog();

        safeStockLog.setSenderGroup(SafeStockLogSenderGroup.RESULT_BATCH);
        safeStockLog.setSenderType(SafeStockLogSenderType.RESULT_SRL);
        safeStockLog.setSender(logSender);
        safeStockLog.setSkuSrl(safeStockParam.getSkuSrl());
        safeStockLog.setSafeAmtRel(newSafeStockAmt);
        safeStockLog.setRegDt(safeStockParam.getChgDt());
        safeStockLog.setSafeRate(safeRate);
        safeStockLog.setStockAmtUnit(safeStockParam.getSafeAmt());
        safeStockLogRepository.save(safeStockLog);
    }

    private void saveSafeStock(SafeStock safeStock, Long newSafeStockAmt, Date chg_dt, String userId) {
        safeStock.setSafeAmt(newSafeStockAmt);
        safeStock.setChgDt(chg_dt);
        safeStock.setChgId(userId);
        safeStockRepository.save(safeStock);
    }
}
