package com.car.order.web.service.consult.impl;

import com.alibaba.fastjson.JSON;
import com.car.account.client.feign.StoreFegin;
import com.car.account.client.feign.TechnicianFegin;
import com.car.account.client.feign.VehicleFegin;
import com.car.account.client.request.profit.AddProfitReq;
import com.car.account.client.request.technician.UpdateTechnicianAccountReq;
import com.car.account.client.response.platform.PlatformStreamRes;
import com.car.account.client.response.store.StoreDetailRes;
import com.car.account.client.response.technician.TechnicianRes;
import com.car.account.client.response.vehicle.vehicleUser.VehicleUserRes;
import com.car.common.enums.*;
import com.car.common.exception.BusinessException;
import com.car.common.res.PageRes;
import com.car.common.res.ResultRes;
import com.car.common.utils.*;
import com.car.common.utils.token.Baggages;
import com.car.order.client.enums.consult.*;
import com.car.order.client.enums.goods.EvaluateStsEnum;
import com.car.order.client.enums.goods.OrderStsEnum;
import com.car.order.client.enums.goods.PayMethodEnum;
import com.car.order.client.enums.platform.PlatformClassifyEnum;
import com.car.order.client.request.content.AddContentReq;
import com.car.order.client.request.order.consult.*;
import com.car.order.client.request.order.order.AddOrderInfoReq;
import com.car.order.client.response.order.consult.ConsultInfoListRes;
import com.car.order.client.response.order.consult.ConsultOrderDetailRes;
import com.car.order.client.response.order.consult.ConsultRes;
import com.car.order.client.response.order.consult.OrderConsultInfoListRes;
import com.car.order.web.common.constants.ConfigConsts;
import com.car.order.web.common.constants.Constants;
import com.car.order.web.mapper.consult.ConsultDetailMapper;
import com.car.order.web.mapper.consult.ConsultImagesMapper;
import com.car.order.web.mapper.consult.ConsultMapper;
import com.car.order.web.mapper.consult.ConsultOrderMapper;
import com.car.order.web.model.consult.Consult;
import com.car.order.web.model.consult.ConsultDetail;
import com.car.order.web.model.consult.ConsultImages;
import com.car.order.web.model.consult.ConsultOrder;
import com.car.order.web.service.consult.OrderConsultService;
import com.car.order.web.service.content.ContentService;
import com.car.order.web.service.order.OrderAccountService;
import com.car.order.web.service.order.OrderInfoService;
import com.car.system.client.feign.SystemFeign;
import com.car.system.client.response.dict.DictionaryRes;
import com.codingapi.txlcn.tc.annotation.TxcTransaction;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.xmlbeans.UserType;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author zhouz
 * @date 2021/1/1
 */
@Slf4j
@Service
public class OrderConsultServiceImpl implements OrderConsultService {

    @Autowired
    ConsultMapper consultMapper;
    @Autowired
    ConsultDetailMapper consultDetailMapper;
    @Autowired
    ConsultImagesMapper consultImagesMapper;
    @Autowired
    ConsultOrderMapper consultOrderMapper;

    @Autowired
    ContentService contentService;

    @Autowired
    ConfigConsts configConsts;
    @Resource
    private TechnicianFegin technicianFegin;
    @Resource
    private VehicleFegin vehicleFegin;

    @Autowired
    private OrderInfoService orderInfoService;

    @Autowired
    SystemFeign systemFeign;

    @Autowired
    private StoreFegin storeFegin;
    @Autowired
    private OrderAccountService orderAccountService;

    /**
     * ??????????????????????????????
     */
    private static final String consultMoneyCode = "consult_amount";
    /**
     * ??????????????????
     * @param addConsultReq
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @TxcTransaction
    public ResultRes<String> addConsultOrder(AddConsultReq addConsultReq) {
        log.debug("??????????????????");
        Consult consult = new Consult();
        String technicianUuid = addConsultReq.getTechnicianUuid();
        BigDecimal answerAmt = null;
        if(StringUtils.isEmpty(technicianUuid)){
            //???????????????ID?????????????????????????????????????????????????????????
            ResultRes<DictionaryRes> dictionaryRes = systemFeign.queryByCode(consultMoneyCode);
            if(!dictionaryRes.isSuccess() || dictionaryRes.getData() == null){
                throw new BusinessException(ResEnum.NON_EXISTENT);
            }
            answerAmt = BigDecimal.valueOf(Double.valueOf(dictionaryRes.getData().getLableValue()));
            consult.setConsultType(ConsultTypeEnum.national_questions.getValue());
        }else{
            ResultRes<TechnicianRes> res = technicianFegin.queryTechnicianDetail(technicianUuid);
            if(!res.isSuccess()){
                log.error("????????????????????????>>>res:{}", JSON.toJSONString(res));
                throw new BusinessException(ResEnum.TECHNICIAN_NOT_EXIST);
            }
            TechnicianRes data = res.getData();
            String photoImgUrl = data.getPhotoImgUrl();
            String mobile = data.getMobile();
            String name = data.getUserName();
            answerAmt = data.getAnswerAmt();
            consult.setTechnicianImgUrl(photoImgUrl);
            consult.setTechnicianName(name);
            consult.setTechnicianMobile(mobile);
            consult.setTechnicianUuid(technicianUuid);
            consult.setConsultType(ConsultTypeEnum.technician_questions.getValue());
        }

        String consultTitle = addConsultReq.getConsultTitle();
        String carOwnerUuid = TokenHelper.getUserUuid();

        String ehicleUuid = "";
        String PhotoImgUrl ="";
        String Mobile = "";
        String Name = "";
        if(UserTypeEnum.vehicle.getType().equals(TokenHelper.getUserType())) {
            ResultRes<VehicleUserRes> rst = vehicleFegin.queryDetail(carOwnerUuid);
            if (!rst.isSuccess()) {
                log.error("????????????????????????>>>rst:{}", JSON.toJSONString(rst));
                log.error("?????????????????????????????????token0???{}", RequestUtil.getRequestHeaderParam("token"));
                log.error("?????????????????????????????????token1???{}", Baggages.getToken());
                throw new BusinessException(ResEnum.VEHICLE_OWNER_NOT_EXIST);
            }

            VehicleUserRes vehicle = rst.getData();
            ehicleUuid = vehicle.getUuid();
            PhotoImgUrl = vehicle.getPhotoImgUrl();
            Mobile = vehicle.getMobile();
            Name = vehicle.getUserName();
        }else {
            if (UserTypeEnum.store.getType().equals(TokenHelper.getUserType())) {
                // ??????????????????????????????????????????,??????????????????
                StoreDetailRes storeDetailRes = queryStoreDetailByUser();
                ehicleUuid = storeDetailRes.getUuid();
                if(storeDetailRes.getShopImgList()!=null) {
                    PhotoImgUrl =storeDetailRes.getShopImgList().get(0);
                }
                Mobile  = TokenHelper.getLoginToken().getUserMobile();
                Name = storeDetailRes.getStoreName();
            } else {
                ehicleUuid = TokenHelper.getUserUuid();
                ResultRes<TechnicianRes> res = technicianFegin.queryTechnicianDetail(ehicleUuid);
                if(!res.isSuccess()){
                    log.error("????????????????????????>>>res:{}", JSON.toJSONString(res));
                    throw new BusinessException(ResEnum.TECHNICIAN_NOT_EXIST);
                }
                TechnicianRes data = res.getData();
                PhotoImgUrl = data.getPhotoImgUrl();
                Mobile = data.getMobile();
                Name = data.getUserName();
            }
        }
        //????????????
        String consultUuid = UuidUtils.getUuid();
        consult.setUuid(consultUuid);
        consult.setCreatedTime(new Date());
        consult.setCreatedBy(TokenHelper.getUserName());
        consult.setSts(StsEnum.ACTIVE.getValue());
        consult.setCarOwnerImgUrl(PhotoImgUrl);
        consult.setCarOwnerName(Name);
        consult.setCarOwnerMobile(Mobile);
        consult.setCarOwnerUuid(ehicleUuid);
        consult.setConsultCheckSts(CheckStatusEnum.CHECK_PENDING.getValue());
        consult.setAnswerCheckSts(CheckStatusEnum.CHECK_PENDING.getValue());
        consult.setAnswerSts(AnswerStsEnum.UNANSWERED.getValue());
        consult.setCarOwnerType(TokenHelper.getUserType());
        consult.setTitle(consultTitle);
        consult.setVehicleBrand(addConsultReq.getVehicleBrand());
        consult.setVehicleModel(addConsultReq.getVehicleModel());

        //????????????????????????
        consultMapper.insert(consult);

        //??????????????????
        ConsultDetail consultDetail = new ConsultDetail();
        consultDetail.setUuid(consultUuid);
        consultDetail.setConsultUuid(consult.getUuid());
        consultDetail.setCreatedTime(new Date());
        consultDetail.setCreatedBy(TokenHelper.getUserName());
        consultDetail.setSts(StsEnum.ACTIVE.getValue());


        consultDetail.setConsultDesc(addConsultReq.getConsultContent());
        consultDetailMapper.insert(consultDetail);

        //????????????????????????
        List<String> consultImgList = addConsultReq.getConsultImgList();
        addConsultImg(consultImgList,consult.getUuid(),ConsultImgTypenum.CONSULT_IMG.getValue());

        //????????????????????????
        String consultOrderUuid = UuidUtils.getUuid();
        ConsultOrder consultOrder = new ConsultOrder();
        consultOrder.setUuid(consultOrderUuid);
        consultOrder.setCreatedTime(new Date());
        consultOrder.setCreatedBy(TokenHelper.getUserName());
        consultOrder.setSts(StsEnum.ACTIVE.getValue());
        consultOrder.setConsultUuid(consultUuid);
        consultOrder.setOrderAmount(answerAmt);
        consultOrder.setReceivableAmount(answerAmt);
        consultOrder.setCarOwnerImgUrl(PhotoImgUrl);
        consultOrder.setCarOwnerName(Name);
        consultOrder.setCarOwnerMobile(Mobile);
        consultOrder.setCarOwnerUuid(ehicleUuid);
        consultOrder.setCarOwnerType(TokenHelper.getUserType());
        consultOrder.setEvaluateSts(EvaluateStsEnum.NO_COMMENT.getValue());
        String orderNo = OrderUtils.GenOrderNo(OrderPrefixEnum.ZX);
        consultOrder.setOrderNum(orderNo);
        consultOrder.setOrderSts(OrderStsEnum.UNPAID.getValue());
        consultOrder.setOrderType(OrderTypeEnum.CONSULT.getValue());

        //??????????????????
        consultOrderMapper.insert(consultOrder);

        //??????order_info??????
        AddOrderInfoReq addOrderInfoReq = new AddOrderInfoReq();
        addOrderInfoReq.setOrderType(OrderTypeEnum.CONSULT.getValue());
        addOrderInfoReq.setOrderUuid(consultOrderUuid);
        orderInfoService.addOrder(addOrderInfoReq);

        return ResultRes.success(consultOrder.getUuid());
    }

    /**
     * ??????????????????
     * @param orderUuid
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @TxcTransaction
    public ResultRes<String> deleteConsultOrder(String orderUuid) {
        log.debug("??????????????????");
        ConsultOrder consultOrder = checkOrderIsActive(orderUuid);
        consultOrder.setSts(StsEnum.INVALID.getValue());
        consultOrderMapper.updateByPrimaryKeySelective(consultOrder);
        return ResultRes.success(orderUuid);
    }

    /**
     * ??????????????????
     * @param addAnswerConsultReq
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @TxcTransaction
    public ResultRes<String> updateConsultAnswer(AddAnswerConsultReq addAnswerConsultReq) {
        log.debug("??????????????????");
        ConsultOrder consultOrder = checkOrderIsActive(addAnswerConsultReq.getOrderUuid());

        log.debug("??????????????????");
        Consult consult = checkConsult(consultOrder.getConsultUuid());
        //???????????? ??????????????????????????????????????? addAnswerConsultReq.getTechnicianUuid()
        String technicianUuid = TokenHelper.getUserUuid();
        if (!technicianUuid.equals(consult.getTechnicianUuid())) {
            log.error("?????????????????? ??????uuid?????????????????????uuid????????? technicianUuid:{}",technicianUuid);
            throw new BusinessException(ResEnum.TECHNICIAN_NO_CONSUL_ORDER);
        }

        ConsultDetail consultDetail = new ConsultDetail();
        consultDetail.setConsultUuid(consultOrder.getConsultUuid());
        consultDetail.setSts(StsEnum.ACTIVE.getValue());
        consultDetail = consultDetailMapper.selectOne(consultDetail);
        if (StringUtils.isEmpty(consultDetail)) {
            log.error("?????????????????? ????????????????????? consultUuid:{}",consultOrder.getConsultUuid());
            throw new BusinessException(ResEnum.NON_EXISTENT);
        }
        consultDetail.setAnswerDesc(addAnswerConsultReq.getAnswerContent());
        consultDetailMapper.updateByPrimaryKeySelective(consultDetail);

        consult.setAnswerSts(AnswerStsEnum.ALREADY_ANSWERED.getValue());
        consult.setAnswerTime(new Date());
        consultMapper.updateByPrimaryKeySelective(consult);

        //????????????????????????
        addConsultImg(addAnswerConsultReq.getAnswerImgList(),consult.getUuid(),ConsultImgTypenum.ANSWER_IMG.getValue());

        //???????????????????????????
        if(!StringUtils.isEmpty(consult.getTechnicianUuid())){
            technicianFegin.updateQaCount(consult.getTechnicianUuid());
        }

        //??????????????????
        addContentCheck(consultOrder.getCarOwnerUuid(),consultOrder.getCarOwnerMobile(),OrderTypeEnum.ANSWER.getValue(),
                consult.getTitle(),addAnswerConsultReq.getAnswerContent(),consultOrder.getUuid(),addAnswerConsultReq.getAnswerImgList());

        return ResultRes.success(consultOrder.getUuid());
    }

    /**
     * ????????????????????????
     * @param updateAcceptResultReq
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @TxcTransaction
    public ResultRes<String> updateAcceptResult(UpdateAcceptResultReq updateAcceptResultReq) {
        log.debug("??????????????????????????????");
        //????????????????????????
        ConsultOrder consultOrder = checkOrderIsActive(updateAcceptResultReq.getOrderUuid());
        //??????????????????????????????
        Consult consult = checkConsult(consultOrder.getConsultUuid());
        String desc = AcceptResultEnum.enumOfDesc(updateAcceptResultReq.getAcceptResult());
        if (StringUtils.isEmpty(desc)) {
            log.error("????????????????????????????????? acceptResult: {}",updateAcceptResultReq.getAcceptResult());
            throw new BusinessException(ResEnum.INVALID_ACCEPT_RESULT);
        }
        consult.setAcceptResult(updateAcceptResultReq.getAcceptResult());
        consultMapper.updateByPrimaryKeySelective(consult);

        return ResultRes.success(consultOrder.getUuid());
    }

    /**
     * ??????????????????
     * @param orderUuid
     */
    @Override
    public ResultRes pushContentCheck(String orderUuid){
        ConsultOrder consultOrder = checkOrderIsActive(orderUuid);
        log.debug("??????????????????");
        Consult consult = checkConsult(consultOrder.getConsultUuid());

        log.debug("??????????????????");
        ConsultDetail consultDetail = new ConsultDetail();
        consultDetail.setConsultUuid(consult.getUuid());
        consultDetail.setSts(StsEnum.ACTIVE.getValue());
        consultDetail = consultDetailMapper.selectOne(consultDetail);
        if (StringUtils.isEmpty(consultDetail)) {
            log.error("???????????????????????? consultUuid:{}",consultOrder.getConsultUuid());
            throw new BusinessException(ResEnum.NON_EXISTENT);
        }
        ConsultImages consultImages = new ConsultImages();
        consultImages.setConsultUuid(consult.getUuid());
        consultImages.setSts(StsEnum.ACTIVE.getValue());
        consultImages.setImgType(ConsultImgTypenum.CONSULT_IMG.getValue());
        List<ConsultImages> consultImagesList = consultImagesMapper.select(consultImages);

        List<String> resourcesList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(consultImagesList)) {
            for (ConsultImages item : consultImagesList) {
                resourcesList.add(item.getImgUrl());
            }
        }
        //??????????????????  ?????????????????????
        addContentCheck(consultOrder.getCarOwnerUuid(),consultOrder.getCarOwnerMobile(),OrderTypeEnum.CONSULT.getValue(),
                consult.getTitle(),consultDetail.getConsultDesc(),consultOrder.getUuid(),resourcesList);

        return ResultRes.success(orderUuid);
    }

    /**
     * ??????????????????
     * @param addAuditorReq
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @TxcTransaction
    public ResultRes<String> addAuditorOrder(AddAuditorReq addAuditorReq) {
        log.debug("??????????????????");
        ConsultOrder consultOrder = checkOrderIsActive(addAuditorReq.getOrderUuid());

        ConsultOrder auditorOrder = new ConsultOrder();
        auditorOrder.setUuid(UuidUtils.getUuid());
        auditorOrder.setCreatedTime(new Date());
        auditorOrder.setCreatedBy(TokenHelper.getUserName());
        auditorOrder.setSts(StsEnum.ACTIVE.getValue());
        auditorOrder.setConsultUuid(consultOrder.getConsultUuid());
        auditorOrder.setOrderType(OrderTypeEnum.AUDITOR.getValue());
        String orderNum = OrderUtils.GenOrderNo(OrderPrefixEnum.PT);
        BigDecimal money = new BigDecimal(configConsts.getAuditorOrderMoney());
        auditorOrder.setOrderAmount(money);
        auditorOrder.setReceivableAmount(money);
        auditorOrder.setOrderNum(orderNum);
        auditorOrder.setEvaluateSts(EvaluateStsEnum.NO_COMMENT.getValue());

        String userUuid = TokenHelper.getUserUuid();
        ResultRes<VehicleUserRes> res = vehicleFegin.queryDetail(userUuid);
        if(res.isSuccess()){
            VehicleUserRes data = res.getData();
            String userName = data.getUserName();
            String mobile = data.getMobile();
            String photoImgUrl = data.getPhotoImgUrl();
            String uuid = data.getUuid();

            auditorOrder.setCarOwnerType(UserTypeEnum.vehicle.getType());
            auditorOrder.setCarOwnerImgUrl(photoImgUrl);
            auditorOrder.setCarOwnerName(userName);
            auditorOrder.setCarOwnerMobile(mobile);
            auditorOrder.setCarOwnerUuid(uuid);
        }else {

            throw new BusinessException(res.getCode(),res.getMsg());
        }


        auditorOrder.setOrderSts(OrderStsEnum.UNPAID.getValue());
        consultOrderMapper.insert(auditorOrder);

        //??????order_info??????
        AddOrderInfoReq addOrderInfoReq = new AddOrderInfoReq();
        addOrderInfoReq.setOrderUuid(auditorOrder.getUuid());
        addOrderInfoReq.setOrderType(OrderTypeEnum.AUDITOR.getValue());
        orderInfoService.addOrder(addOrderInfoReq);

        return ResultRes.success(auditorOrder.getUuid());
    }


    /**
     * ??????????????????
     * @param addAuditorReq
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @TxcTransaction
    public ResultRes<String> addAuditorOrderTwo(AddAuditorReq addAuditorReq) {
        log.debug("????????????/??????????????????");
        ConsultOrder consultOrder = checkOrderIsActive(addAuditorReq.getOrderUuid());

        ConsultOrder auditorOrder = new ConsultOrder();
        auditorOrder.setUuid(UuidUtils.getUuid());
        auditorOrder.setCreatedTime(new Date());
        auditorOrder.setCreatedBy(TokenHelper.getUserName());
        auditorOrder.setSts(StsEnum.ACTIVE.getValue());
        auditorOrder.setConsultUuid(consultOrder.getConsultUuid());
        auditorOrder.setOrderType(OrderTypeEnum.AUDITOR.getValue());
        String orderNum = OrderUtils.GenOrderNo(OrderPrefixEnum.PT);
        BigDecimal money = new BigDecimal(configConsts.getAuditorOrderMoney());
        auditorOrder.setOrderAmount(money);
        auditorOrder.setReceivableAmount(money);
        auditorOrder.setOrderNum(orderNum);
        auditorOrder.setEvaluateSts(EvaluateStsEnum.NO_COMMENT.getValue());
        Integer userType = TokenHelper.getUserType();
        String userUuid = TokenHelper.getUserUuid();

        if(UserTypeEnum.technician.getType().equals(userType)) {
            ResultRes<TechnicianRes>  resT  = technicianFegin.queryTechnicianDetail(userUuid);
            if (!resT.isSuccess()) {
                log.error("????????????????????????>>>oederuuId:{}",addAuditorReq.getOrderUuid());
                throw new BusinessException(ResEnum.NON_EXISTENT);
            }
            TechnicianRes technicianRes = resT.getData();
            auditorOrder.setCarOwnerImgUrl(technicianRes.getPhotoImgUrl());
            auditorOrder.setCarOwnerName(technicianRes.getUserName());
            auditorOrder.setCarOwnerMobile(technicianRes.getMobile());
            auditorOrder.setCarOwnerUuid(technicianRes.getUuid());
        }else if(UserTypeEnum.store.getType().equals(userType)){
            ResultRes<StoreDetailRes> resS = storeFegin.queryStoreDetailByUser();
            if(resS.getData()!=null) {
                resS = storeFegin.queryStoreDetail(resS.getData().getUuid());
                StoreDetailRes  storeDetailRes= resS.getData();
                auditorOrder.setCarOwnerImgUrl(storeDetailRes.getShopImgList().get(0));
                auditorOrder.setCarOwnerName(storeDetailRes.getStoreName());
                auditorOrder.setCarOwnerMobile(TokenHelper.getLoginToken().getUserMobile());
                auditorOrder.setCarOwnerUuid(storeDetailRes.getUuid());
            }

        }

        auditorOrder.setOrderSts(OrderStsEnum.UNPAID.getValue());
        auditorOrder.setCarOwnerType(userType);
        consultOrderMapper.insert(auditorOrder);

        //??????order_info??????
        AddOrderInfoReq addOrderInfoReq = new AddOrderInfoReq();
        addOrderInfoReq.setOrderUuid(auditorOrder.getUuid());
        addOrderInfoReq.setOrderType(OrderTypeEnum.AUDITOR.getValue());
        orderInfoService.addOrder(addOrderInfoReq);

        return ResultRes.success(auditorOrder.getUuid());
    }

    /**
     * ??????????????????
     * @param payConsultReq
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @TxcTransaction
    public ResultRes<String> payAuditorOrder(PayConsultReq payConsultReq) {
        log.debug("??????????????????");
        ConsultOrder consultOrder = checkOrderIsActive(payConsultReq.getOrderUuid());
        //??????????????????????????????
        checkPayType(payConsultReq.getPayType());
        consultOrder.setPayType(payConsultReq.getPayType());
        //TODO
        //???????????? ????????????????????????????????? ????????????
        consultOrder.setOrderSts(OrderStsEnum.HAVE_PAID.getValue());
        consultOrderMapper.updateByPrimaryKeySelective(consultOrder);

        return ResultRes.success(consultOrder.getUuid());
    }

    /**
     * ??????????????????????????????
     * @param payType
     */
    private void checkPayType(Integer payType) {
        String desc = PayMethodEnum.enumOfDesc(payType);
        if (StringUtils.isEmpty(desc)) {
            log.error("???????????? ????????????????????? payType:{}",payType);
            throw new BusinessException(ResEnum.INVALID_CHECK_STS);
        }
    }

    /**
     * ??????????????????
     * @param orderUuid
     * @param orderType
     * @param checkSts
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @TxcTransaction
    public void updateConsultCheckSts(String orderUuid, Integer orderType, Integer checkSts) {
        log.debug("??????????????????");
        ConsultOrder consultOrder = checkOrderIsActive(orderUuid);
        log.debug("??????????????????");
        Consult consult = checkConsult(consultOrder.getConsultUuid());

        if (OrderTypeEnum.CONSULT.getValue().equals(orderType)) {
            consult.setConsultCheckSts(checkSts);
        } else if (OrderTypeEnum.ANSWER.getValue().equals(orderType)) {
            consult.setAnswerCheckSts(checkSts);
            if(CheckStatusEnum.APPROVE.getValue().equals(checkSts)){

                PlatformStreamRes platformStreamRes = orderAccountService.getPlatformStreamRes(orderType, consultOrder.getOrderNum());
                UpdateTechnicianAccountReq req = new UpdateTechnicianAccountReq();
                req.setTechnicianUuid(consult.getTechnicianUuid());
                req.setOrderAmount(platformStreamRes.getAmt());
                ResultRes<String> resultRes = technicianFegin.updateTechnicianAccount(req);
                if (!resultRes.isSuccess()) {
                    log.error("?????????????????????????????????????????????{}", JSON.toJSONString(req));
                    throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
                }
                orderAccountService.addProfit(consultOrder.getOrderNum(), orderType,platformStreamRes.getAmt() , consult.getTechnicianUuid(), UserTypeEnum.technician.getType(), StreamTypeEnum.IN.getType());
                consultOrder.setOrderSts(OrderStsEnum.COMPLETED.getValue());
                consultOrder.setLastUpdatedTime(new Date());
                int updateOrderGoodsNum = consultOrderMapper.updateByPrimaryKeySelective(consultOrder);
                if (updateOrderGoodsNum <= 0) {
                    log.error("??????????????????/???????????????????????????????????????????????????{} ", JSON.toJSONString(consultOrder));
                    throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
                }
            }
        }
        consultMapper.updateByPrimaryKeySelective(consult);
    }

    /**
     * ??????????????????
     * @param param
     * @return
     */
    @Override
    public PageRes<List<ConsultInfoListRes>> queryConsultList(QueryConsultListReq param) {
        log.debug("??????????????????");
        PageHelper.startPage(param.getPageNum(), param.getPageSize());
        List<ConsultInfoListRes> consultInfoList = consultOrderMapper.queryConsultList(param);
        PageInfo<ConsultInfoListRes> pageInfo = new PageInfo<>(consultInfoList);
        return PageRes.success(consultInfoList, pageInfo.getPageSize(), (int) pageInfo.getTotal(), pageInfo.getPages());
    }

    /**
     * ????????????????????????
     * @param param
     * @return
     */
    @Override
    public PageRes<List<OrderConsultInfoListRes>> queryOrderConsultList(QueryOrderConsultListReq param) {
        log.debug("????????????????????????");
        PageHelper.startPage(param.getPageNum(), param.getPageSize());
        List<OrderConsultInfoListRes> orderConsultInfoList = consultOrderMapper.queryOrderConsultList(param);
        PageInfo<OrderConsultInfoListRes> pageInfo = new PageInfo<>(orderConsultInfoList);
        return PageRes.success(orderConsultInfoList, pageInfo.getPageSize(), (int) pageInfo.getTotal(), pageInfo.getPages());
    }

    /**
     * ????????????????????????
     * @param uuid
     * @return
     */
    @Override
    public ResultRes<ConsultOrderDetailRes> queryOrderConsultDetail(String uuid) {
        log.debug("????????????????????????");
        ConsultOrderDetailRes consultOrderDetailRes = null;
        ConsultOrder consultOrder = new ConsultOrder();
        consultOrder.setSts(StsEnum.ACTIVE.getValue());
        consultOrder.setUuid(uuid);
        consultOrder.setOrderType(OrderTypeEnum.CONSULT.getValue());
        consultOrder = consultOrderMapper.selectOne(consultOrder);
        if (!StringUtils.isEmpty(consultOrder)) {
            consultOrderDetailRes = new ConsultOrderDetailRes();
            BeanUtils.copyProperties(consultOrder,consultOrderDetailRes);

            log.debug("??????????????????");
            Consult consult = new Consult();
            consult.setUuid(consultOrder.getConsultUuid());
            consult.setSts(StsEnum.ACTIVE.getValue());
            consult = consultMapper.selectOne(consult);
            if (!StringUtils.isEmpty(consult)) {
                ConsultRes consultRes = new ConsultRes();
                BeanUtils.copyProperties(consult,consultRes);
                consultRes.setConsultAmt(consultOrder.getOrderAmount());
                consultOrderDetailRes.setAcceptResult(consult.getAcceptResult());
                consultOrderDetailRes.setConsultRes(consultRes);
                consultOrderDetailRes.setVehicleBrand(consult.getVehicleBrand());
                consultOrderDetailRes.setVehicleModel(consult.getVehicleModel());

                log.debug("??????????????????");
                ConsultDetail consultDetail = new ConsultDetail();
                consultDetail.setConsultUuid(consult.getUuid());
                consultDetail.setSts(StsEnum.ACTIVE.getValue());
                consultDetail = consultDetailMapper.selectOne(consultDetail);
                if (!StringUtils.isEmpty(consultDetail)) {
                    consultRes.setConsultDesc(consultDetail.getConsultDesc());
                    //??????????????????????????????????????????
//                    if (CheckStatusEnum.APPROVE.getValue().equals(consult.getAnswerCheckSts())) {
                        consultRes.setAnswerDesc(consultDetail.getAnswerDesc());
//                    }
                }

                ConsultImages consultImages = new ConsultImages();
                consultImages.setConsultUuid(consult.getUuid());
                consultImages.setSts(StsEnum.ACTIVE.getValue());
                //??????????????????????????????????????????
//                if (!CheckStatusEnum.APPROVE.getValue().equals(consult.getAnswerCheckSts())) {
//                    log.debug("?????????????????????????????????????????????");
//                    consultImages.setImgType(ConsultImgTypenum.CONSULT_IMG.getValue());
//                }
                log.debug("????????????????????????");
                List<ConsultImages> consultImagesList = consultImagesMapper.select(consultImages);
                if (!CollectionUtils.isEmpty(consultImagesList)) {
                   List<String> consultImgList = new ArrayList<>();
                   List<String> answerImgList = new ArrayList<>();

                   for (ConsultImages item : consultImagesList) {
                       if (ConsultImgTypenum.CONSULT_IMG.getValue().equals(item.getImgType())) {
                           consultImgList.add(item.getImgUrl());
                       } else if (ConsultImgTypenum.ANSWER_IMG.getValue().equals(item.getImgType())) {
                           answerImgList.add(item.getImgUrl());
                       }
                   }
                    consultRes.setConsultImgList(consultImgList);
                    consultRes.setAnswerImgList(answerImgList);
                }
                consultOrderDetailRes.setConsultRes(consultRes);
            }
        }
        return ResultRes.success(consultOrderDetailRes);
    }

    /**
     * ????????????????????????
     * @param exportReq
     * @param response
     */
    @Override
    public void exportOrderConsultList(QueryOrderConsultListReq exportReq, HttpServletResponse response) {
        log.debug("????????????????????????");
        try {
            List<OrderConsultInfoListRes> orderConsultInfoList = consultOrderMapper.queryOrderConsultList(exportReq);
            //??????????????????
            InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(Constants.ORDER_CONSULT_INFO_EXPORT_TEMPLATE);
            //????????????????????????
            List<OrderConsultInfoListRes> excelList = ExcelUtils.setFieldValue(orderConsultInfoList);
            Workbook wb = new XSSFWorkbook(resourceAsStream);
            Sheet sheet = wb.getSheetAt(0);
            //????????????????????????
            int firstRowIndex = sheet.getFirstRowNum()+2;
            for (int rowIndex = firstRowIndex; rowIndex < excelList.size()+2; rowIndex++) {
                //?????????
                Row rowStyle = (rowIndex % 2) == 0?sheet.getRow(2): sheet.getRow(3);
                //????????????
                CellStyle cellStyle = ExcelUtils.getExcelFormat(rowStyle.getCell(1));
                CellStyle cellStyle1 = ExcelUtils.getExcelFormat(rowStyle.getCell(0));
                Row row = sheet.getRow(rowIndex);
                if(row == null){
                    row = sheet.createRow(rowIndex);
                }
                row.setHeight(rowStyle.getHeight());
                OrderConsultInfoListRes exportDto = excelList.get(rowIndex - 2);
                ExcelUtils.setCell(row,cellStyle1,0,rowIndex-1);
                ExcelUtils.setCell(row,cellStyle,1,exportDto.getCarOwnerName());
                ExcelUtils.setCell(row,cellStyle,2, exportDto.getCreatedTime());
                ExcelUtils.setCell(row,cellStyle,3,exportDto.getTechnicianName());

                ExcelUtils.setCell(row,cellStyle,4,exportDto.getTechnicianMobile());
                ExcelUtils.setCell(row,cellStyle,5, OrderTypeEnum.enumOfDesc(exportDto.getOrderType()));
                ExcelUtils.setCell(row,cellStyle,6, "?? "+exportDto.getOrderAmount());
                ExcelUtils.setCell(row,cellStyle,7,exportDto.getCarOwnerMobile());
                ExcelUtils.setCell(row,cellStyle,8,  PayMethodEnum.enumOfDesc(exportDto.getPayType()));
                ExcelUtils.setCell(row,cellStyle,9, OrderStsEnum.enumOfDesc(exportDto.getOrderSts()));
                ExcelUtils.setCell(row,cellStyle,10, AnswerStsEnum.enumOfDesc(exportDto.getAnswerSts()));
            }
            ExcelUtils.responseWrite(wb,response, Constants.ORDER_CONSULT_INFO_EXPORT_TEMPLATE);
        } catch (Exception ex){
            log.error("????????????????????????????????????????????????{}", ExceptionUtils.stackTraceToString(ex));
        }
    }

    /**
     * ????????????????????????
     * @param orderUuid
     */
    private ConsultOrder checkOrderIsActive(String orderUuid) {
        ConsultOrder consultOrder = new ConsultOrder();
        consultOrder.setUuid(orderUuid);
        consultOrder.setSts(StsEnum.ACTIVE.getValue());
        consultOrder = consultOrderMapper.selectOne(consultOrder);
        if (StringUtils.isEmpty(consultOrder)) {
            log.error("??????????????? orderUuid:{}",orderUuid);
            throw new BusinessException(ResEnum.NON_EXISTENT);
        }
        return consultOrder;
    }

    /**
     * ??????????????????
     * @param carOwnerUuid
     * @param carOwnerMobile
     * @param orderType
     * @param orderName
     * @param checkContentDesc
     * @param orderUuid
     * @param resourcesList
     */
    private void addContentCheck(String carOwnerUuid, String carOwnerMobile, Integer orderType, String orderName, String checkContentDesc, String orderUuid, List<String> resourcesList) {
        AddContentReq addContentReq = new AddContentReq();
        addContentReq.setUserUuid(carOwnerUuid);
        addContentReq.setMobile(carOwnerMobile);
        addContentReq.setOrderType(orderType);
        addContentReq.setOrderName(orderName);
        addContentReq.setContentDetail(checkContentDesc);
        addContentReq.setOrderUuid(orderUuid);
        addContentReq.setResourcesList(resourcesList);
        contentService.addContent(addContentReq);
    }

    /**
     * ????????????????????????
     * @param consultImgList
     * @param consultUuid
     * @param imgType
     */
    private void addConsultImg(List<String> consultImgList,String consultUuid,Integer imgType) {
//        if (ConsultImgTypenum.ANSWER_IMG.getValue().equals(imgType)) {
//            // ??????????????????????????????????????????
//            ConsultImages deleteConsultImages = new ConsultImages();
//            deleteConsultImages.setImgType(imgType);
//            deleteConsultImages.setConsultUuid(consultUuid);
//            consultImagesMapper.delete(deleteConsultImages);
//        }
        if (!CollectionUtils.isEmpty(consultImgList)) {
            List<ConsultImages> consultImagesList = new ArrayList<>();
            ConsultImages consultImages;
            for (String imgUrl : consultImgList) {
                consultImages = new ConsultImages();
                consultImages.setUuid(UuidUtils.getUuid());
                consultImages.setCreatedTime(new Date());
                consultImages.setCreatedBy(TokenHelper.getUserName());
                consultImages.setSts(StsEnum.ACTIVE.getValue());
                consultImages.setConsultUuid(consultUuid);
                consultImages.setImgUrl(imgUrl);
                consultImages.setImgType(imgType);
                consultImagesList.add(consultImages);
            }

            log.info("consultImagesList:{}",JSON.toJSONString(consultImagesList));
            consultImagesMapper.batchInsertConsultImages(consultImagesList);
        }
    }

    /**
     * ????????????ID??????????????????
     * @param consultUuid
     * @return consult
     */
    @Override
    public Consult checkConsult(String consultUuid) {
        Consult consult = new Consult();
        consult.setUuid(consultUuid);
        consult.setSts(StsEnum.ACTIVE.getValue());
        consult = consultMapper.selectOne(consult);
        if (StringUtils.isEmpty(consult)) {
            log.error("???????????????????????? consultUuid:{}",consultUuid);
            throw new BusinessException(ResEnum.NON_EXISTENT);
        }
        return consult;
    }


    /**
     * ?????????????????????????????????
     * @param uuid
     * @return
     */
    @Override
    public ResultRes<String> consultOrderSnapUp(String uuid) {
        if(!UserTypeEnum.technician.getType().equals(TokenHelper.getUserType())){
            throw new BusinessException(ResEnum.SUPPORT_ONLY_TECHNICIAN_ERROR);
        }
        Consult consult = consultMapper.selectByPrimaryKey(uuid);
        if(StringUtils.isEmpty(consult)){
            throw new BusinessException(ResEnum.NON_EXISTENT);
        }
        if(!StringUtils.isEmpty(consult.getTechnicianUuid())){
            //????????????????????????
            throw new BusinessException(ResEnum.CONSULT_ORDER_SNAP_UP_ERROR);
        }
        //??????????????????
        ResultRes<TechnicianRes> technicianRes = technicianFegin.queryTechnicianDetail(TokenHelper.getUserUuid());
        if(!technicianRes.isSuccess()){
            throw new BusinessException(ResEnum.NON_EXISTENT);
        }
        TechnicianRes technician = technicianRes.getData();
        consult.setTechnicianUuid(technician.getUuid());
        consult.setTechnicianImgUrl(technician.getPhotoImgUrl());
        consult.setTechnicianMobile(technician.getMobile());
        consult.setTechnicianName(technician.getUserName());
        consultMapper.updateByPrimaryKey(consult);
        return ResultRes.success();
    }

    /**
     * ??????????????????????????????????????????
     * @return
     */
    private StoreDetailRes queryStoreDetailByUser() {
        ResultRes<StoreDetailRes> storeDetailResResultRes = storeFegin.queryStoreDetailByUser();
        if (!storeDetailResResultRes.isSuccess()) {
            log.error("????????????????????????????????????????????? >>>storeDetailResResultRes:{}", JSON.toJSONString(storeDetailResResultRes));
            throw new BusinessException(ResEnum.STORE_INFO_NOT_BY_USER);
        }
        StoreDetailRes storeDetailRes = storeDetailResResultRes.getData();
        return storeDetailRes;
    }


}
