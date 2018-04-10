package tool;

import java.util.*;

/**
 * POMS에서 안전재고 업데이트 하는 정책 구현클래스
 */
@Component
public class PomsSafeStockSetter extends SafeStockSetter {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    SafeStockRepository safeStockRepository;
    @Autowired
    SafeStockLogRepository safeStockLogRepository;
    @Autowired
    SafeStockMailService mailService;
    @Autowired
    SkuMappingInfoRepository skuMappingRepository;
    @Autowired
    ResultRepository resultRepository;

    @Override
    public SafeStockSetterType getSafeStockSetterType() {
        return SafeStockSetterType.POMS;
    }

    /**
     * 센터코드 가져오는 방법
     sku로 안전재고 테이블 검색
     1개만 반환되면 -> 그 안전재고 데이터 업데이트
     1개 이상 반환되면 -> 모든 안전재고 데이터 업데이트 + 경고 이메일 + 에러로그 남기기
     하나도 반환이 안된다면 ->   (1) 맵핑 테이블에서 센터코드 가져오기
     (2) 실적 테이블에서 센터코드 가져오기 (centerGroup호출하여 isPrimary로 filter 한후 센터코드 가져오기)
     (3) throw Exception (안전재고를 업데이트 하기에 정보가 없습니다)
     */
    @Override
    @SuppressWarnings(value = "unchecked")
    public Map<String, Object> work(SafeStock safeStockParam) {
        Map<String, Object> resultMap = new HashMap<>();

        // 안전재고 로그
        saveSafeStockLog(safeStockParam, safeStockParam.getChgId(), resultMap);

        final Integer skuSrl = safeStockParam.getSkuSrl();
        List<SafeStock> safeStockList = safeStockRepository.findBySkuSrl(skuSrl);
        int safeStockListSize = CollectionUtils.isEmpty(safeStockList) ? 0 : safeStockList.size();

        if (safeStockListSize == 1) {
            // 1개만 반환되면 -> 그 안전재고 데이터 업데이트
            List<SafeStock> result = updateSafeStockList(safeStockList, safeStockParam);
            setResultMapSuccess(resultMap, safeStockParam.getSafeAmt(), result);

        } else if (safeStockListSize > 1) {
            // 1개 이상 반환되면 -> 모든 안전재고 데이터 업데이트 + 경고 이메일 + 에러로그 남기기
            List<SafeStock> result = updateSafeStockList(safeStockList, safeStockParam);
            setResultMapSuccess(resultMap, safeStockParam.getSafeAmt(), result);

            try {
                String errorMessage = composeErrorMessage(skuSrl, safeStockListSize, "\n");
                logger.error(errorMessage);
                errorMessage = composeErrorMessage(skuSrl, safeStockListSize, "</br></br>");
                mailService.sendErrorMail(errorMessage);
            } catch (RpcResponseException e) {
                // Do nothing. sending email is not essential task.
            }

        } else {
            // 0개 반환 -> 센터코드 가져오기 (맵핑 -> 실적 -> 예외처리)
            String centerCode = getCenterCode(skuSrl);
            if (StringUtils.isEmpty(centerCode)) {
                throw new WmsLogicalException(String.format("해당 SKU(%d)는 안전재고 수량을 업데이트 하기에 적절하지 않습니다. SKU(%d) 번호 확인 바랍니다.", skuSrl, skuSrl));
            }
            SafeStock resultSafeStock = insertSaveStock(safeStockParam, centerCode);
            setResultMapSuccess(resultMap, 0L, Arrays.asList(resultSafeStock));
        }

        return resultMap;
    }

    private List<SafeStock> updateSafeStockList(List<SafeStock> safeStockList, SafeStock safeStockParam) {
        for (SafeStock safeStockItem : safeStockList) {
            safeStockItem.setSafeAmt(safeStockParam.getSafeAmt());
            safeStockItem.setChgDt(safeStockParam.getChgDt());
            safeStockItem.setChgId(safeStockParam.getChgId());
        }
        return safeStockRepository.save(safeStockList);
    }

    private void saveSafeStockLog(SafeStock safeStockParam, String logSender, Map<String, Object> resultMap) {
        SafeStockLog safeStockLog = new SafeStockLog();
        safeStockLog.setSenderGroup(SafeStockLogSenderGroup.POMS_ADMIN);
        safeStockLog.setSenderType(SafeStockLogSenderType.USER_ID);
        safeStockLog.setSender(logSender);
        safeStockLog.setSkuSrl(safeStockParam.getSkuSrl());
        safeStockLog.setSafeAmtRel(0L);
        safeStockLog.setSafeAmtAbs(safeStockParam.getSafeAmt());
        safeStockLog.setStockAmtUnit(safeStockParam.getSafeAmt());
        safeStockLog.setRegDt(safeStockParam.getChgDt());
        SafeStockLog result = safeStockLogRepository.save(safeStockLog);

        if (Objects.isNull(result)) {
            resultMap.put("RET_CODE", "-1");
            resultMap.put("RET_REASON", "로그 입력 불가");
        }
    }

    private SafeStock insertSaveStock(SafeStock safeStockParam, String centerCode) {
        SafeStock newSafeStock = new SafeStock();
        newSafeStock.setSkuSrl(safeStockParam.getSkuSrl());
        newSafeStock.setCenterCode(centerCode);
        newSafeStock.setSafeRate(0L);
        newSafeStock.setSafeAmt(0L);
        newSafeStock.setRegDt(safeStockParam.getChgDt());
        newSafeStock.setRegId(safeStockParam.getChgId());
        newSafeStock.setChgDt(safeStockParam.getChgDt());
        newSafeStock.setChgId(safeStockParam.getChgId());

        return safeStockRepository.save(newSafeStock);
    }

    private void setResultMapSuccess(Map<String, Object> resultMap, Long safeAmt, List<SafeStock> result) {
        if (CollectionUtils.isNotEmpty(result) && Objects.nonNull(result.get(0))) {
            resultMap.put("RET_CODE", "0");
            resultMap.put("RET_REASON", "SUCCESS");
            resultMap.put("RET_RESULT", safeAmt);
        }
    }

    private String getCenterCode(Integer skuSrl) {
        String centerCode;
        centerCode = skuMappingRepository.findRecentCenterCodeBySkuSrl(skuSrl);
        if (StringUtils.isNotEmpty(centerCode)) {
            return centerCode;
        }
        centerCode = resultRepository.findRecentPrimaryCenterCode(skuSrl);
        if (StringUtils.isNotEmpty(centerCode)) {
            return centerCode;
        }
        return centerCode;
    }

    private String composeErrorMessage(Integer skuSrl, int numOfUpdates, String separator) {
        StringBuilder builder = new StringBuilder();
        builder.append("안전재고 비정상 업데이트!");
        builder.append(separator);
        builder.append(String.format("동일한 SKU(%d)의 안전재고 데이터가 총 %d개 업데이트 되었습니다(정상 케이스: 1개의 SKU에는 1개의 안전재고 데이터만 존재해야 합니다).%s", skuSrl, numOfUpdates, separator));
        builder.append("업데이트 된 복수의 안전재고 데이터 삭제작업 필요!");
        builder.append(separator);

        return builder.toString();
    }
}
