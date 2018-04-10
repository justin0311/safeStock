package service;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.tmoncorp.wms.api.common.client.WmsApiRestClient;
import com.tmoncorp.wms.api.safestock.dao.SafeStockDao;
import com.tmoncorp.wms.api.safestock.repository.SafeStockRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class SafeStockService {
    @Autowired
    SafeStockRepository safeStockRepository;
    @Autowired
    SafeStockLogRepository safeStockLogRepository;
    @Autowired
    ResultRepository resultRepository;
    @Autowired
    SkuMappingInfoRepository skuMappingRepository;
    @Autowired
    SafeStockDao safeStockDao;
    @Autowired
    WmsApiRestClient wmsApiRestClient;
    @Autowired
    StockSyncService stockSyncService;
    @Autowired
    List<SafeStockSetter> safeStockSetterList;

    /**
     * 맵핑 안전재고 업데이트
     */
    @Transactional(value = "wmsTransactionManager")
    public boolean updateSafeRate(Integer skuSrl, String centerCode, Long safeRate, String userId) throws WmsLogicalException {
        return safeStockSetterList.stream()
                .filter(setter -> SafeStockSetterType.MAPPING.equals(setter.getSafeStockSetterType()))
                .findAny()
                .map((safeStockSetter) -> {
                    SafeStock safeStockParam = new SafeStock();
                    safeStockParam.setSkuSrl(skuSrl);
                    safeStockParam.setCenterCode(centerCode);
                    safeStockParam.setSafeRate(safeRate);
                    safeStockParam.setChgId(userId);
                    safeStockParam.setChgDt(new Date());
                    return (boolean) safeStockSetter.work(safeStockParam);
                })
                .orElseThrow(() -> new WmsLogicalException(
                        String.format("%s is not Found! Need to implement the class & its corresponding Enum Class!", getClass().getName())));
    }

    /**
     * 입고배치 안전재고 업데이트
     * SafeStockSetterType 이 정의되지 않을때, 예외 던지지 않는 이유는 입고배치 중 예외발생 하면 안되기 때문에
     */
    @Transactional(value = "wmsTransactionManager")
    public void updateSafeStockAmount(List<SafeStockAmtRequest> safeStockAmtRequestList) {
        Date date = new Date();

        safeStockSetterList.stream()
                .filter(setter -> SafeStockSetterType.RECEIPT.equals(setter.getSafeStockSetterType()))
                .findAny()
                .ifPresent(safeStockSetter -> {
                    for (SafeStockAmtRequest safeStockReq : safeStockAmtRequestList) {
                        SafeStock safeStockParam = new SafeStock();
                        safeStockParam.setSkuSrl(safeStockReq.getSkuSrl());
                        safeStockParam.setCenterCode(safeStockReq.getCenterCode());
                        safeStockParam.setSafeAmt(safeStockReq.getSkuQty());
                        safeStockParam.setChgDt(date);
                        safeStockParam.setChgId(String.valueOf(safeStockReq.getResultSrl()));
                        safeStockSetter.work(safeStockParam);
                    }
                });
    }

    /**
     * POMS 안전재고 업데이트
     */
    @Transactional(value = "wmsTransactionManager")
    public Map<String, Object> updateSkuSafeAmount(String sender, Integer sku, Long safeAmt) throws WmsLogicalException, IllegalArgumentException {
        return safeStockSetterList.stream()
                .filter(setter -> SafeStockSetterType.POMS.equals(setter.getSafeStockSetterType()))
                .findAny()
                .map(safeStockSetter -> {
                    SafeStock safeStockParam = new SafeStock();
                    safeStockParam.setSkuSrl(sku);
                    safeStockParam.setSafeAmt(safeAmt);
                    safeStockParam.setChgDt(new Date());
                    safeStockParam.setChgId(sender);
                    return (Map<String, Object>) safeStockSetter.work(safeStockParam);
                })
                .orElseThrow(() -> new WmsLogicalException(
                        String.format("%s is not Found! Need to implement the class & its corresponding Enum Class!", getClass().getName())));
    }
}
