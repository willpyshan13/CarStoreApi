package com.car.order.web.service.dtc.impl;

import com.alibaba.fastjson.JSON;
import com.car.account.client.feign.StoreFegin;
import com.car.account.client.feign.StoreUserFeign;
import com.car.account.client.response.store.StoreUserRes;
import com.car.common.enums.CheckStatusEnum;
import com.car.common.enums.ResEnum;
import com.car.common.enums.StsEnum;
import com.car.common.enums.UserTypeEnum;
import com.car.common.exception.BusinessException;
import com.car.common.res.PageRes;
import com.car.common.res.ResultRes;
import com.car.common.utils.ExcelUtils;
import com.car.common.utils.StringUtil;
import com.car.common.utils.TokenHelper;
import com.car.common.utils.UuidUtils;
import com.car.order.client.enums.goods.OrderStsEnum;
import com.car.order.client.enums.order.DtcIssuerTypeEnum;
import com.car.order.client.request.dtc.AddDtcReq;
import com.car.order.client.request.dtc.CheckDtcReq;
import com.car.order.client.request.dtc.QueryDtcListReq;
import com.car.order.client.response.dtc.QueryDtcInfoRes;
import com.car.order.client.response.dtc.QueryDtcListRes;
import com.car.order.client.response.dtc.QueryDtcOrderInfoRes;
import com.car.order.web.common.constants.ConfigConsts;
import com.car.order.web.mapper.dtc.DtcContentMapper;
import com.car.order.web.mapper.dtc.DtcMapper;
import com.car.order.web.mapper.dtc.DtcOrderMapper;
import com.car.order.web.model.dtc.Dtc;
import com.car.order.web.model.dtc.DtcContent;
import com.car.order.web.model.dtc.DtcOrder;
import com.car.order.web.service.dtc.DtcService;
import com.codingapi.txlcn.tc.annotation.TxcTransaction;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * Created by Intellij IDEA.
 *
 * @author: ??cjw
 * Date: ??2021/2/17
 */
@Slf4j
@Service
public class DtcServiceImpl implements DtcService {

    @Autowired
    private DtcMapper dtcMapper;

    @Autowired
    private DtcContentMapper dtcContentMapper;

    @Autowired
    private ConfigConsts configConsts;

    @Autowired
    private StoreUserFeign storeUserFeign;

    @Autowired
    StoreFegin storeFegin;

    @Autowired
    private DtcOrderMapper dtcOrderMapper;

    /**
     * ??????id????????????
     * @param uuid
     * @return
     */
    @Override
    public ResultRes<QueryDtcInfoRes> getById(String uuid) {
        if (StringUtils.isEmpty(uuid)) {
            log.error("uuid????????????");
            throw new BusinessException(ResEnum.LACK_PARAMETER);
        }
        QueryDtcInfoRes queryDtcInfoRes = dtcMapper.getById(uuid);

        QueryDtcInfoRes res = new QueryDtcInfoRes();
        //??????dtc???????????????
        Pair<Integer,String> pair = getDtcIssuerType();
        if (!StringUtils.isEmpty(queryDtcInfoRes)) {
            if (pair.getFirst().equals(DtcIssuerTypeEnum.ADMIN.getType())) {
                BeanUtils.copyProperties(queryDtcInfoRes, res);
            } else if (pair.getFirst().equals(DtcIssuerTypeEnum.TECHNICIAN.getType())) {

                //????????????????????????dtc??????
                checkDtcInfoExaminePermission(pair,queryDtcInfoRes,res);
            } else if (pair.getFirst().equals(DtcIssuerTypeEnum.STORE.getType())) {
                //???????????????????????????????????????
                checkDtcInfoExaminePermission(pair,queryDtcInfoRes,res);
            }
            res.setUuid(uuid);
        }
        return ResultRes.success(res);
    }

    private QueryDtcInfoRes checkDtcInfoExaminePermission(Pair<Integer, String> pair, QueryDtcInfoRes queryDtcInfoRes,QueryDtcInfoRes res) {
        // ??????token????????????????????????????????????
        if (pair.getSecond().equals(queryDtcInfoRes.getDtcIssuerUuid())) {
            // ??????token?????????????????????????????????????????????dtc???????????? ??????????????????
            BeanUtils.copyProperties(queryDtcInfoRes, res);
            res.setIsOneself(true);
        } else {
            // ??????token?????????????????????????????????????????????dtc???????????? ??????????????????
            //???????????????????????????????????????????????????3
            DtcOrder dtcOrder = dtcOrderMapper.queryPurchaseOrder(pair.getSecond(), queryDtcInfoRes.getUuid());
            if (!StringUtils.isEmpty(dtcOrder)) {
                if (OrderStsEnum.HAVE_PAID.getValue().equals(dtcOrder.getOrderSts())) {
                    //???????????????????????????????????????
                    updateDtcOrder(dtcOrder.getUuid());
                    BeanUtils.copyProperties(queryDtcInfoRes, res);
                } else {
                    res.setDtcAmount(dtcOrder.getOrderAmount());
                    res.setOrderUuid(dtcOrder.getUuid());
                }
                res.setOrderSts(dtcOrder.getOrderSts());
            } else {
                res.setDtcAmount(queryDtcInfoRes.getDtcAmount());
            }
            res.setIsOneself(false);
        }
        return res;
    }

    /**
     *??????????????????????????????
     */
    private void updateDtcOrder (String dtcOrderUuid) {
        //??????????????????
        QueryDtcOrderInfoRes queryDtcOrderInfoRes = dtcOrderMapper.getById(dtcOrderUuid);
        //??????????????????????????????
        DtcOrder dtcOrderUpdate = new DtcOrder();
        dtcOrderUpdate.setUuid(queryDtcOrderInfoRes.getUuid());
        dtcOrderUpdate.setReadCount(queryDtcOrderInfoRes.getReadCount() + 1);
        int updateDtcOrder = dtcOrderMapper.updateByPrimaryKeySelective(dtcOrderUpdate);
        if (updateDtcOrder <= 0) {
            log.error("??????dtcOrder?????????????????????????????????{}", queryDtcOrderInfoRes.getUuid());
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }
    }
    /**
     * ??????DTC??????
     * @param req
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @TxcTransaction
    public ResultRes<String> add(AddDtcReq req) {
        //??????????????????
        String userName = TokenHelper.getUserName();
        //??????dtc??????????????????
        if (StringUtils.isEmpty(req.getDtcAmount())) {
            req.setDtcAmount(configConsts.getDtcOrderMoney());
        }

        //??????dtc???????????????
        Pair<Integer,String> pair = getDtcIssuerType();

        Dtc dtc = new Dtc();
        dtc.setUuid(UuidUtils.getUuid());
        dtc.setDtcIssuerUuid(pair.getSecond());
        dtc.setDtcCode(req.getDtcCode());
        dtc.setDtcType(req.getDtcType());
        dtc.setDtcDefinition(req.getDtcDefinition());
        dtc.setDtcBrandUuid(req.getDtcBrandUuid());
        dtc.setDtcAmount(new BigDecimal(req.getDtcAmount()));
        dtc.setDtcIssuerType(pair.getFirst());
        dtc.setSts(StsEnum.ACTIVE.getValue());
        dtc.setCreatedTime(new Date());
        dtc.setDtcCheckSts(CheckStatusEnum.CHECK_PENDING.getValue());
        dtc.setCreatedBy(userName);
        int insertDtcNum = dtcMapper.insert(dtc);
        if (insertDtcNum <= 0) {
            log.error("??????dtc???????????????????????????????????????{}", JSON.toJSONString(dtc));
            throw new BusinessException(ResEnum.INSERT_DB_ERROR);
        }
        DtcContent dtcContent = new DtcContent();
        dtcContent.setUuid(UuidUtils.getUuid());
        dtcContent.setDtcUuid(dtc.getUuid());
        dtcContent.setDtcExplain(req.getDtcExplain());
        dtcContent.setDtcReasons(req.getDtcReasons());
        dtcContent.setDtcDiagnose(req.getDtcDiagnose());
        dtcContent.setSts(StsEnum.ACTIVE.getValue());
        dtcContent.setCreatedBy(userName);
        dtcContent.setCreatedTime(new Date());
        int insertDtcContent = dtcContentMapper.insert(dtcContent);
        if (insertDtcContent <= 0) {
            log.error("??????dtc?????????????????????????????????????????????{}", JSON.toJSONString(dtcContent));
            throw new BusinessException(ResEnum.INSERT_DB_ERROR);
        }
        return ResultRes.success(dtc.getUuid());
    }

    /**
     * ??????id??????dtc??????
     * @param req
     * @param uuid
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @TxcTransaction
    public ResultRes<String> updateById(AddDtcReq req, String uuid) {
        if (StringUtils.isEmpty(uuid)) {
            log.error("??????dtc?????????uuid????????????");
            throw new BusinessException(ResEnum.LACK_PARAMETER);
        }
        //??????dtc??????????????????
        if (StringUtils.isEmpty(req.getDtcAmount())) {
            req.setDtcAmount(configConsts.getDtcOrderMoney());
        }
        //??????dtc???????????????
        Pair<Integer,String> pair = getDtcIssuerType();
        //????????????
        String userName = TokenHelper.getUserName();
        Dtc dtcUpdate = new Dtc();
        dtcUpdate.setUuid(uuid);
        dtcUpdate.setDtcCode(req.getDtcCode());
        dtcUpdate.setDtcDefinition(req.getDtcDefinition());
        dtcUpdate.setDtcBrandUuid(req.getDtcBrandUuid());
        dtcUpdate.setDtcIssuerType(pair.getFirst());
        dtcUpdate.setDtcType(req.getDtcType());
        dtcUpdate.setDtcAmount(new BigDecimal(req.getDtcAmount()));
        dtcUpdate.setLastUpdatedTime(new Date());
        dtcUpdate.setLastUpdatedBy(userName);
        dtcUpdate.setDtcCheckSts(req.getDtcCheckSts());
        dtcUpdate.setDtcRemarks(req.getDtcRemarks());
        //??????dtc????????????
        int dtcUpdateNum = dtcMapper.updateByPrimaryKeySelective(dtcUpdate);
        if (dtcUpdateNum <= 0) {
            log.error("??????dtc???????????????????????????????????????{}, uuid:{}", JSON.toJSONString(dtcUpdate), uuid);
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }
        //??????dtc???????????????
        int updateDtcContentNum = dtcContentMapper.updateDtcContentInfo(req, uuid, userName);
        if (updateDtcContentNum <= 0) {
            log.error("??????dtc?????????????????????????????????{}, uuid:{}", JSON.toJSONString(req), uuid);
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }
        return ResultRes.success(uuid);
    }

    /**
     * ??????dtc??????
     * @param uuid
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @TxcTransaction
    public ResultRes<String> deleteById(String uuid) {
        if (StringUtils.isEmpty(uuid)) {
            log.error("??????dtc?????????uuid???????????????");
            throw new BusinessException(ResEnum.LACK_PARAMETER);
        }
        //????????????uuid
        String userUuid = TokenHelper.getUserUuid();
        //??????????????????
        String userName = TokenHelper.getUserName();
        //??????dtc??????
        int deleteDtcNum = dtcMapper.deleteDtcInfo(uuid, userName);
        if (deleteDtcNum <= 0) {
            log.error("??????dtc????????????");
            throw new BusinessException(ResEnum.PAY_ERROR);
        }
        //??????dtc??????????????????
        int deleteDtcContent = dtcContentMapper.deleteDtcContentInfo(uuid, userName);
        if (deleteDtcContent <= 0) {
            log.error("??????dtc??????????????????");
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }

        return ResultRes.success(uuid);
    }

    /**
     * ????????????
     * @param req
     * @return
     */
    @Override
    public PageRes<List<QueryDtcListRes>> list(QueryDtcListReq req) {
        PageHelper.startPage(req.getPageNum(), req.getPageSize());
        List<QueryDtcListRes> list = dtcMapper.queryDtcList(req);
        PageInfo<QueryDtcListRes> pageInfo = new PageInfo<>(list);
        return PageRes.success(list, pageInfo.getPageSize(), (int) pageInfo.getTotal(), pageInfo.getPages());
    }

    @Override
    public ResultRes<String> checkDtc( CheckDtcReq req){
        log.debug("??????DTC");
        Dtc d = new  Dtc();
        d.setUuid(req.getDtcUuid());
        d = dtcMapper.selectByPrimaryKey(d);
        if (StringUtils.isEmpty(d) || StsEnum.INVALID.getValue().equals(d.getSts())) {
            log.error("??????DTC ???????????????????????? uuid {}",d.getUuid());
            throw new BusinessException(ResEnum.NON_EXISTENT);
        }
        String checkDesc = CheckStatusEnum.enumOfDesc(req.getDtcCheckSts());
        if (StringUtils.isEmpty(checkDesc)) {
            log.error("??????DTC ??????????????????????????? checkSt:{}",req.getDtcCheckSts());
            throw new BusinessException(ResEnum.INVALID_CHECK_STS);
        }
        if (!d.getDtcCheckSts().equals(req.getDtcCheckSts())) {
            d.setDtcCheckSts(req.getDtcCheckSts());
            d.setDtcRemarks(req.getDtcRemarks());
            dtcMapper.updateByPrimaryKey(d);
        }
        return ResultRes.success(d.getUuid());
    }

    /**
     * ????????????
     * @param file
     * @return
     */
    @Override
    public ResultRes batchImport(MultipartFile file,String brandUuid,String dtcTypeUuid) {
        try {
            //???????????????
            InputStream input = file.getInputStream();
            Workbook wb;
            //??????????????????(2003??????2007)????????????
            if(file.getOriginalFilename().endsWith("xlsx")){
                wb = new XSSFWorkbook(input);
            }else{
                wb = new HSSFWorkbook(input);
            }
            //?????????????????????
            wb.getNumberOfSheets();
            Sheet sheet = wb.getSheetAt(0);
            int rowCount = sheet.getLastRowNum()+1;
            for(int i = 1; i < rowCount;i++){
                String dtcCode = ExcelUtils.getSheetValue(sheet,i,1);
                if(StringUtils.isEmpty(dtcCode)){
                    continue;
                }
                //????????????
                String dtcDefinition = ExcelUtils.getSheetValue(sheet,i,3);
                //????????????
                String dtcExplain = ExcelUtils.getSheetValue(sheet,i,4);
                //????????????
                String dtcReasons = ExcelUtils.getSheetValue(sheet,i,5);
                //????????????
                String dtcDiagnose = ExcelUtils.getSheetValue(sheet,i,6);

                Dtc dtc = new Dtc();
                dtc.setUuid(UuidUtils.getUuid());
                dtc.setDtcIssuerUuid("0");
                dtc.setDtcCode(dtcCode);
                dtc.setDtcDefinition(dtcDefinition);
                dtc.setDtcBrandUuid(brandUuid);
                dtc.setDtcAmount(new BigDecimal(0.5));
                dtc.setDtcIssuerType(0);
                dtc.setSts(StsEnum.ACTIVE.getValue());
                dtc.setDtcType(dtcTypeUuid);
                dtc.setCreatedTime(new Date());
                dtc.setCreatedBy("admin");
                int insertDtcNum = dtcMapper.insert(dtc);
                if (insertDtcNum <= 0) {
                    log.error("??????dtc???????????????????????????????????????{}", JSON.toJSONString(dtc));
                    throw new BusinessException(ResEnum.INSERT_DB_ERROR);
                }
                DtcContent dtcContent = new DtcContent();
                dtcContent.setUuid(UuidUtils.getUuid());
                dtcContent.setDtcUuid(dtc.getUuid());
                dtcContent.setDtcExplain(dtcExplain);
                dtcContent.setDtcReasons(dtcReasons);
                dtcContent.setDtcDiagnose(dtcDiagnose);
                dtcContent.setSts(StsEnum.ACTIVE.getValue());
                dtcContent.setCreatedBy("admin");
                dtcContent.setCreatedTime(new Date());
                dtcContentMapper.insert(dtcContent);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return ResultRes.success();
    }

    /**
     * ??????token??????dtc???????????????
     * @return
     */
    private Pair<Integer,String> getDtcIssuerType() {
        //????????????uuid
        String issuerUuid = TokenHelper.getUserUuid();
        //??????????????????
        Integer dtcIssuerType = DtcIssuerTypeEnum.ADMIN.getType();

        Integer userType = TokenHelper.getUserType();
        if (UserTypeEnum.vehicle.getType().equals(userType)) {
            //??????
            throw new BusinessException(ResEnum.DTC_NOT_VEHICLE_ISSUER);
        } else if (UserTypeEnum.technician.getType().equals(userType)) {
            //??????
            dtcIssuerType = DtcIssuerTypeEnum.TECHNICIAN.getType();
        } else if (UserTypeEnum.store.getType().equals(userType)) {
            //??????
            dtcIssuerType = DtcIssuerTypeEnum.STORE.getType();
            ResultRes<StoreUserRes> resResultRes = storeUserFeign.queryStoreUserInfo(issuerUuid);
            if (!resResultRes.isSuccess() || StringUtils.isEmpty(resResultRes.getData())) {
                log.error("??????????????????uuid");
                throw new BusinessException(ResEnum.NON_EXISTENT);
            }
            StoreUserRes storeUserRes = resResultRes.getData();
            issuerUuid = storeUserRes.getStoreUuid();
        }
        return Pair.of(dtcIssuerType,issuerUuid);
    }

    /**
     * ????????????????????????????????????Dtc?????????????????????
     * @param userUuid
     */
    private void checkUserDtc (QueryDtcInfoRes res, String userUuid) {
    }
}
