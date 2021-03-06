package com.car.order.web.service.pay.impl;

import com.alibaba.fastjson.JSON;
import com.car.account.client.feign.*;
import com.car.account.client.request.platform.PlatformStreamReq;
import com.car.account.client.request.profit.AddProfitReq;
import com.car.account.client.request.store.UpdateStoreAccountReq;
import com.car.account.client.request.technician.UpdateTechnicianAccountReq;
import com.car.account.client.response.goods.GoodsRes;
import com.car.common.enums.*;
import com.car.common.exception.BusinessException;
import com.car.common.res.ResultRes;
import com.car.common.utils.DateUtil;
import com.car.common.utils.DigitUtils;
import com.car.common.utils.IpUtils;
import com.car.common.utils.TokenHelper;
import com.car.order.client.enums.consult.OrderTypeEnum;
import com.car.order.client.enums.goods.OrderStsEnum;
import com.car.order.client.enums.goods.PayMethodEnum;
import com.car.order.client.enums.goods.SceneOrderStsEnum;
import com.car.order.client.enums.platform.PlatformClassifyEnum;
import com.car.order.client.enums.sharetechnicianorder.ShareTechnicianOrderEnum;
import com.car.order.client.request.order.order.PayReq;
import com.car.order.web.common.constants.Constants;
import com.car.order.web.dto.PayOrderInfoDto;
import com.car.order.web.mapper.consult.ConsultMapper;
import com.car.order.web.mapper.consult.ConsultOrderMapper;
import com.car.order.web.mapper.course.CourseMapper;
import com.car.order.web.mapper.course.CourseOrderMapper;
import com.car.order.web.mapper.dtc.DtcOrderDetailMapper;
import com.car.order.web.mapper.dtc.DtcOrderMapper;
import com.car.order.web.mapper.goods.OrderGoodsMapper;
import com.car.order.web.mapper.instance.OrderCaseMapper;
import com.car.order.web.mapper.order.OrderInfoMapper;
import com.car.order.web.mapper.scene.SceneOrderMapper;
import com.car.order.web.mapper.sharetechnicianorder.ShareTechnicianOrderMapper;
import com.car.order.web.mapper.technician.TechnicianCaseMapper;
import com.car.order.web.model.consult.Consult;
import com.car.order.web.model.consult.ConsultOrder;
import com.car.order.web.model.course.Course;
import com.car.order.web.model.course.CourseOrder;
import com.car.order.web.model.dtc.DtcOrder;
import com.car.order.web.model.dtc.DtcOrderDetail;
import com.car.order.web.model.goods.OrderGoods;
import com.car.order.web.model.instance.OrderCase;
import com.car.order.web.model.order.OrderInfo;
import com.car.order.web.model.scene.SceneOrder;
import com.car.order.web.model.sharetechnicianorder.ShareTechnicianOrder;
import com.car.order.web.model.technician.cases.TechnicianCase;
import com.car.order.web.service.consult.OrderConsultService;
import com.car.order.web.service.pay.PayService;
import com.car.system.client.feign.SystemFeign;
import com.car.system.client.response.dict.DictionaryRes;
import com.car.utility.client.enums.ChannelTypeEnum;
import com.car.utility.client.feign.PayFeign;
import com.car.utility.client.request.pay.CreateOrderReq;
import com.car.utility.client.response.pay.CreateOrderRes;
import com.codingapi.txlcn.tc.annotation.TxcTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Date;

/**
 * Created by Intellij IDEA.
 *
 * @author: ??cjw
 * Date: ??2021/2/7
 */
@Slf4j
@Service
public class PayServiceImpl implements PayService {

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderGoodsMapper orderGoodsMapper;

    @Autowired
    private ConsultOrderMapper consultOrderMapper;

    @Autowired
    private ConsultMapper consultMapper;

    @Autowired
    private OrderCaseMapper orderCaseMapper;

    @Autowired
    private DtcOrderMapper dtcOrderMapper;

    @Autowired
    private DtcOrderDetailMapper dtcOrderDetailMapper;

    @Autowired
    private TechnicianCaseMapper technicianCaseMapper;

    @Autowired
    private PayFeign payFeign;

    @Autowired
    private CourseOrderMapper courseOrderMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private StoreFegin storeFegin;

    @Autowired
    private SceneOrderMapper sceneOrderMapper;

    @Autowired
    private ShareTechnicianOrderMapper shareTechnicianOrderMapper;

    @Autowired
    private TechnicianFegin technicianFegin;

    @Autowired
    private SystemFeign systemFeign;

    @Autowired
    private OrderConsultService orderConsultService;

    @Autowired
    private GoodsFeign goodsFeign;

    @Autowired
    private PlatformStreamFeign platformStreamFeign;

    @Autowired
    private ProfitStreamFeign profitStreamFeign;

    /**
     * ??????????????????
     *
     * @param req
     * @param request
     * @return
     */
    @Override
    public ResultRes<CreateOrderRes> payConsultOrder(PayReq req, HttpServletRequest request) {
        //??????????????????
        PayOrderInfoDto payOrderInfoDto = queryOrderInfo(req.getOrderUuid());
        if (null == payOrderInfoDto) {
            log.error("???????????????????????????");
            throw new BusinessException(ResEnum.NON_EXISTENT);
        }
        //?????????????????????null???0
        if (null == payOrderInfoDto.getPayAmount() || payOrderInfoDto.getPayAmount().compareTo(BigDecimal.ZERO) == 0) {
            log.error("?????????????????????0");
            throw new BusinessException(ResEnum.INVALID_ORDER_AMOUNT);
        }
        //????????????????????????
        updateOrderPayType(req.getOrderUuid(), req.getChannelType());

        CreateOrderReq createOrderReq = new CreateOrderReq();
        createOrderReq.setOrderNo(req.getOrderUuid());
        createOrderReq.setPayAmount(payOrderInfoDto.getPayAmount());
        createOrderReq.setOrderTime(DateUtil.dateToStr(payOrderInfoDto.getOrderTime(), DateUtil.YYYY_MM_DD_HH_MM_SS));
        createOrderReq.setSceneInfo(req.getSceneInfo());
        createOrderReq.setGoodsName(payOrderInfoDto.getGoodsName());
        createOrderReq.setGoodsDesc(Constants.PAY_GOODS_DESC);
        createOrderReq.setClientIp(IpUtils.getRequestIp(request));
        createOrderReq.setChannelType(req.getChannelType());
        createOrderReq.setPaymentType(req.getPaymentType());
        createOrderReq.setReturnUrl(req.getReturnUrl());
        ResultRes<CreateOrderRes> resResultRes = payFeign.createPayOrder(createOrderReq);
        if (!resResultRes.isSuccess()) {
            log.error("?????????????????????????????????{}??????????????????{}", JSON.toJSONString(createOrderReq), JSON.toJSONString(resResultRes));
            throw new BusinessException(ResEnum.PAY_ERROR);
        }
        return ResultRes.success(resResultRes.getData());
    }

    /**
     * ????????????????????????
     */
    private void updateOrderPayType(String orderUuid, String payType) {
        //??????????????????????????????
        String userName = TokenHelper.getUserName();
        //????????????
        Integer orderPayType = null;
        //???????????????????????????
        if (ChannelTypeEnum.weixin.getValue().equals(payType)) {
            //??????????????????
            orderPayType = PayMethodEnum.WE_CHAT_PAY.getValue();
        } else if (ChannelTypeEnum.alipay.getValue().equals(payType)) {
            //?????????????????????
            orderPayType = PayMethodEnum.ALI_PAY.getValue();
        } else {
            log.error("???????????????????????????????????????uuid??????{}?????????????????????{}", orderUuid, payType);
            throw new BusinessException(ResEnum.NON_EXISTENT);
        }
        //??????orderInfo????????????
        OrderInfo orderInfo = orderInfoMapper.queryOrderInfo(orderUuid);
        if (null == orderInfo) {
            log.error("?????????????????????????????? orderUuid {}", orderUuid);
            throw new BusinessException(ResEnum.NON_EXISTENT);
        }
        //??????????????????????????????
        if (OrderTypeEnum.CONSULT.getValue().equals(orderInfo.getOrderType())) {
            //????????????????????????????????????
            this.updateConsultOrderPayType(orderInfo.getOrderUuid(), userName, orderPayType);

        } else if (OrderTypeEnum.GOOD.getValue().equals(orderInfo.getOrderType())) {
            //????????????????????????????????????
            this.updateGoodsOrderPayType(orderInfo.getOrderUuid(), userName, orderPayType);

        } else if (OrderTypeEnum.EXAMPLE.getValue().equals(orderInfo.getOrderType())) {
            //??????????????????????????????
            this.updateCaseOrderPayType(orderInfo.getOrderUuid(), userName, orderPayType);

        } else if (OrderTypeEnum.AUDITOR.getValue().equals(orderInfo.getOrderType())) {
            //??????????????????????????????
            this.updateConsultOrderPayType(orderInfo.getOrderUuid(), userName, orderPayType);

        } else if (OrderTypeEnum.DTC.getValue().equals(orderInfo.getOrderType())) {
            //??????dtc????????????????????????
            this.updateDtcOrderPayType(orderInfo.getOrderUuid(), userName, orderPayType);

        } else if (OrderTypeEnum.COURSE.getValue().equals(orderInfo.getOrderType())) {
            //??????????????????????????????
            this.updateCourseOrderPayType(orderInfo.getOrderUuid(), userName, orderPayType);

        } else if (OrderTypeEnum.SCENE.getValue().equals(orderInfo.getOrderType())) {
            //??????????????????????????????
            this.updateSceneOrderPayType(orderInfo.getOrderUuid(), userName, orderPayType);

        } else if (OrderTypeEnum.SHARED_TECHNICIAN.getValue().equals(orderInfo.getOrderType())) {
            //??????????????????????????????
            this.updateShareTechnicianOrderPayType(orderInfo.getOrderUuid(), userName, orderPayType);

        }
    }

    /**
     * ??????????????????????????????
     *
     * @param orderUuid ??????uuid
     * @param userName  ????????????
     * @param payType   ????????????
     */
    private void updateConsultOrderPayType(String orderUuid, String userName, Integer payType) {
        ConsultOrder consultOrder = new ConsultOrder();
        consultOrder.setUuid(orderUuid);
        consultOrder.setPayType(payType);
        consultOrder.setLastUpdatedBy(userName);
        consultOrder.setLastUpdatedTime(new Date());
        int updateOrderGoodsNum = consultOrderMapper.updateByPrimaryKeySelective(consultOrder);
        if (updateOrderGoodsNum <= 0) {
            log.error("??????????????????/???????????????????????????????????????????????????{} ", JSON.toJSONString(consultOrder));
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }

    }

    /**
     * ??????????????????????????????
     */
    private void updateGoodsOrderPayType(String goodsOrderUuid, String userName, Integer payType) {
        OrderGoods orderGoods = new OrderGoods();
        orderGoods.setUuid(goodsOrderUuid);
        orderGoods.setPayType(payType);
        orderGoods.setLastUpdatedBy(userName);
        orderGoods.setLastUpdatedTime(new Date());
        int updateOrderGoodsNum = orderGoodsMapper.updateByPrimaryKeySelective(orderGoods);
        if (updateOrderGoodsNum <= 0) {
            log.error("?????????????????????????????????????????????????????????????????????{} ", JSON.toJSONString(orderGoods));
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }
    }

    /**
     * ????????????????????????
     *
     * @param caseOrderUuid
     * @param userName
     * @param payType
     */
    private void updateCaseOrderPayType(String caseOrderUuid, String userName, Integer payType) {
        OrderCase orderCase = new OrderCase();
        orderCase.setUuid(caseOrderUuid);
        orderCase.setPayType(payType);
        orderCase.setLastUpdatedBy(userName);
        orderCase.setLastUpdatedTime(new Date());
        int updateOrderGoodsNum = orderCaseMapper.updateByPrimaryKeySelective(orderCase);
        if (updateOrderGoodsNum <= 0) {
            log.error("?????????????????????????????????????????????????????????????????????{} ", JSON.toJSONString(orderCase));
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }
    }


    /**
     * ??????DTC????????????
     *
     * @param dtcOrderUuid
     * @param userName
     * @param payType
     */
    private void updateDtcOrderPayType(String dtcOrderUuid, String userName, Integer payType) {
        DtcOrder dtcOrder = new DtcOrder();
        dtcOrder.setUuid(dtcOrderUuid);
        dtcOrder.setPayType(payType);
        dtcOrder.setLastUpdatedBy(userName);
        dtcOrder.setLastUpdatedTime(new Date());
        int updateOrderDtcNum = dtcOrderMapper.updateByPrimaryKeySelective(dtcOrder);
        if (updateOrderDtcNum <= 0) {
            log.error("??????Dtc???????????????????????????????????????????????????{} ", JSON.toJSONString(dtcOrder));
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }
    }

    /**
     * ??????????????????????????????
     *
     * @param courseOrderUuid
     * @param userName
     * @param payType
     */
    private void updateCourseOrderPayType(String courseOrderUuid, String userName, Integer payType) {
        CourseOrder courseOrder = new CourseOrder();
        courseOrder.setUuid(courseOrderUuid);
        courseOrder.setPayType(payType);
        courseOrder.setLastUpdatedBy(userName);
        courseOrder.setLastUpdatedTime(new Date());
        int updateOrderDtcNum = courseOrderMapper.updateByPrimaryKeySelective(courseOrder);
        if (updateOrderDtcNum <= 0) {
            log.error("???????????????????????????????????????????????????????????????{} ", JSON.toJSONString(courseOrder));
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }
    }

    /**
     * ????????????????????????????????????
     *
     * @param shareTechnicianUuid
     * @param userName
     * @param payType
     */
    private void updateShareTechnicianOrderPayType(String shareTechnicianUuid, String userName, Integer payType) {
        ShareTechnicianOrder shareTechnicianOrder = new ShareTechnicianOrder();
        shareTechnicianOrder.setUuid(shareTechnicianUuid);
        shareTechnicianOrder.setPayType(payType);
        shareTechnicianOrder.setLastUpdatedBy(userName);
        shareTechnicianOrder.setLastUpdatedTime(new Date());
        int updateNum = shareTechnicianOrderMapper.updateByPrimaryKeySelective(shareTechnicianOrder);
        if (updateNum <= 0) {
            log.error("???????????????????????????????????????????????????????????????{} ", JSON.toJSONString(shareTechnicianOrder));
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }
    }


    /**
     * ??????????????????????????????
     *
     * @param sceneOrderUuid
     * @param userName
     * @param payType
     */
    private void updateSceneOrderPayType(String sceneOrderUuid, String userName, Integer payType) {
        SceneOrder sceneOrder = new SceneOrder();
        sceneOrder.setUuid(sceneOrderUuid);
        sceneOrder.setPayType(payType);
        sceneOrder.setLastUpdatedBy(userName);
        sceneOrder.setLastUpdatedTime(new Date());
        int updateNum = sceneOrderMapper.updateByPrimaryKeySelective(sceneOrder);
        if (updateNum <= 0) {
            log.error("?????????????????????????????????????????????????????????????????????{} ", JSON.toJSONString(sceneOrder));
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }
    }

    /**
     * ??????????????????
     *
     * @param orderUuid
     * @return
     */
    private PayOrderInfoDto queryOrderInfo(String orderUuid) {
        //????????????????????????
        OrderInfo orderInfo = orderInfoMapper.queryOrderInfo(orderUuid);
        if (null == orderInfo) {
            log.error("?????????????????????????????? orderUuid {}", orderUuid);
            throw new BusinessException(ResEnum.NON_EXISTENT);
        }
        PayOrderInfoDto payOrderInfoDto = new PayOrderInfoDto();
        //????????????????????????
        if (OrderTypeEnum.CONSULT.getValue().equals(orderInfo.getOrderType())) {
            //??????????????????????????????
            payOrderInfoDto = queryConsultInfo(orderUuid);

        } else if (OrderTypeEnum.GOOD.getValue().equals(orderInfo.getOrderType())) {
            //??????????????????????????????
            payOrderInfoDto = queryGoodOrder(orderUuid);

        } else if (OrderTypeEnum.EXAMPLE.getValue().equals(orderInfo.getOrderType())) {
            //????????????????????????
            payOrderInfoDto = queryCaseOrder(orderUuid);

        } else if (OrderTypeEnum.AUDITOR.getValue().equals(orderInfo.getOrderType())) {
            //????????????????????????
            payOrderInfoDto = queryConsultInfo(orderUuid);

        } else if (OrderTypeEnum.DTC.getValue().equals(orderInfo.getOrderType())) {
            //??????dtc??????????????????
            payOrderInfoDto = queryDtcOrder(orderUuid);

        } else if (OrderTypeEnum.COURSE.getValue().equals(orderInfo.getOrderType())) {
            //????????????????????????
            payOrderInfoDto = queryCourseOrder(orderUuid);

        } else if (OrderTypeEnum.SCENE.getValue().equals(orderInfo.getOrderType())) {
            //??????????????????????????????
            payOrderInfoDto = querySceneOrder(orderUuid);

        } else if (OrderTypeEnum.SHARED_TECHNICIAN.getValue().equals(orderInfo.getOrderType())) {
            //??????????????????????????????
            payOrderInfoDto = queryShareTechnicianOrder(orderUuid);

        }
        return payOrderInfoDto;
    }

    /**
     * ??????????????????
     *
     * @param orderUuid
     * @return
     */
    private PayOrderInfoDto queryCourseOrder(String orderUuid) {
        PayOrderInfoDto payOrderInfoDto = new PayOrderInfoDto();
        CourseOrder courseOrderSelect = new CourseOrder();
        courseOrderSelect.setUuid(orderUuid);
        courseOrderSelect.setSts(StsEnum.ACTIVE.getValue());
        CourseOrder courseOrder = courseOrderMapper.selectOne(courseOrderSelect);
        if (null != courseOrder) {
            payOrderInfoDto.setPayAmount(courseOrder.getOrderAmount());
            payOrderInfoDto.setGoodsName(courseOrder.getCourseTitle());
            payOrderInfoDto.setOrderTime(courseOrder.getCreatedTime());
        }
        return payOrderInfoDto;
    }

    /**
     * ??????????????????????????????
     *
     * @param orderUuid
     * @return
     */
    private PayOrderInfoDto querySceneOrder(String orderUuid) {
        PayOrderInfoDto payOrderInfoDto = new PayOrderInfoDto();
        SceneOrder sceneOrderSelect = new SceneOrder();
        sceneOrderSelect.setUuid(orderUuid);
        sceneOrderSelect.setSts(StsEnum.ACTIVE.getValue());
        SceneOrder sceneOrder = sceneOrderMapper.selectOne(sceneOrderSelect);
        if (null != sceneOrder) {
            payOrderInfoDto.setPayAmount(sceneOrder.getTotalAmount());
            payOrderInfoDto.setGoodsName(Constants.PAY_SCENE_NAME);
            payOrderInfoDto.setOrderTime(sceneOrder.getCreatedTime());
        }
        return payOrderInfoDto;
    }

    /**
     * ??????????????????????????????
     *
     * @param orderUuid
     * @return
     */
    private PayOrderInfoDto queryShareTechnicianOrder(String orderUuid) {
        PayOrderInfoDto payOrderInfoDto = new PayOrderInfoDto();
        ShareTechnicianOrder shareTechnicianOrder = shareTechnicianOrderMapper.queryOrderShareTechnicianOrderInfo(orderUuid);
        if (null == shareTechnicianOrder) {
            log.error("????????????????????????????????????????????????");
            throw new BusinessException(ResEnum.NON_EXISTENT);
        }
        payOrderInfoDto.setGoodsName(Constants.PAY_SHARE_TECHNICIAN_NAME);
        payOrderInfoDto.setPayAmount(shareTechnicianOrder.getPayNum());
        payOrderInfoDto.setOrderTime(shareTechnicianOrder.getCreatedTime());
        return payOrderInfoDto;
    }

    /**
     * ??????dtc??????????????????
     *
     * @param orderUuid
     * @return
     */
    private PayOrderInfoDto queryDtcOrder(String orderUuid) {
        PayOrderInfoDto payOrderInfoDto = new PayOrderInfoDto();
        DtcOrder dtcOrder = new DtcOrder();
        dtcOrder.setSts(StsEnum.ACTIVE.getValue());
        dtcOrder.setUuid(orderUuid);
        dtcOrder = dtcOrderMapper.selectOne(dtcOrder);
        if (StringUtils.isEmpty(dtcOrder)) {
            log.error("??????????????????dtc??????????????????");
            throw new BusinessException(ResEnum.NON_EXISTENT);
        }
        DtcOrderDetail orderDetail = new DtcOrderDetail();
        orderDetail.setSts(StsEnum.ACTIVE.getValue());
        orderDetail.setOrderUuid(orderUuid);
        orderDetail = dtcOrderDetailMapper.selectOne(orderDetail);
        if (StringUtils.isEmpty(orderDetail)) {
            log.error("??????????????????dtc????????????????????????");
            throw new BusinessException(ResEnum.NON_EXISTENT);
        }
        payOrderInfoDto.setGoodsName(orderDetail.getDtcCode());
        payOrderInfoDto.setPayAmount(dtcOrder.getOrderAmount());
        payOrderInfoDto.setOrderTime(dtcOrder.getCreatedTime());
        return payOrderInfoDto;
    }

    /**
     * ????????????????????????
     */
    private PayOrderInfoDto queryCaseOrder(String orderUuid) {
        PayOrderInfoDto payOrderInfoDto = new PayOrderInfoDto();
        OrderCase orderCaseSelect = new OrderCase();
        orderCaseSelect.setUuid(orderUuid);
        orderCaseSelect.setSts(StsEnum.ACTIVE.getValue());
        OrderCase orderCase = orderCaseMapper.selectOne(orderCaseSelect);
        if (null == orderCase) {
            log.error("????????????????????????????????????");
            throw new BusinessException(ResEnum.NON_EXISTENT);
        }
        TechnicianCase technicianCaseSelect = new TechnicianCase();
        technicianCaseSelect.setUuid(orderCase.getCaseUuid());
        TechnicianCase technicianCase = technicianCaseMapper.selectOne(technicianCaseSelect);
        if (null != technicianCase) {
            payOrderInfoDto.setGoodsName(technicianCase.getTitle());
            payOrderInfoDto.setPayAmount(orderCase.getReceivableAmount());
            payOrderInfoDto.setOrderTime(orderCase.getCreatedTime());
        }
        return payOrderInfoDto;
    }

    /**
     * ??????????????????????????????
     */
    private PayOrderInfoDto queryGoodOrder(String orderUuid) {
        PayOrderInfoDto payOrderInfoDto = new PayOrderInfoDto();
        OrderGoods orderGoodsSelect = new OrderGoods();
        orderGoodsSelect.setUuid(orderUuid);
        orderGoodsSelect.setSts(StsEnum.ACTIVE.getValue());
        OrderGoods orderGoods = orderGoodsMapper.selectOne(orderGoodsSelect);
        if (null != orderGoods) {
            BigDecimal amount = orderGoods.getActualAmount();
//            if (ReceiveMethodEnum.EXPRESS.getCode().equals(orderGoods.getDeliveryMode()) ) {
//                amount = DigitUtils.subtract(amount, orderGoods.getAmtService());
//            } else {
//                amount = orderGoods.getActualAmount();
//            }
            payOrderInfoDto.setGoodsName(orderGoods.getGoodsName());
            payOrderInfoDto.setPayAmount(amount);
            payOrderInfoDto.setOrderTime(orderGoods.getCreatedTime());
        }
        return payOrderInfoDto;
    }

    /**
     * ????????????/??????????????????
     *
     * @param orderUuid
     * @return
     */
    private PayOrderInfoDto queryConsultInfo(String orderUuid) {
        PayOrderInfoDto payOrderInfoDto = new PayOrderInfoDto();
        ConsultOrder consultOrderSelect = new ConsultOrder();
        consultOrderSelect.setUuid(orderUuid);
        consultOrderSelect.setSts(StsEnum.ACTIVE.getValue());
        ConsultOrder consultOrder = consultOrderMapper.selectOne(consultOrderSelect);
        if (null == consultOrder) {
            log.error("??????????????????????????????????????????");
            throw new BusinessException(ResEnum.NON_EXISTENT);
        }
        Consult consultSelect = new Consult();
        consultSelect.setUuid(consultOrder.getConsultUuid());
        //????????????????????????
        Consult consult = consultMapper.selectOne(consultSelect);
        if (null != consult) {
            payOrderInfoDto.setGoodsName(consult.getTitle());
            payOrderInfoDto.setPayAmount(consultOrder.getReceivableAmount());
            payOrderInfoDto.setOrderTime(consultOrder.getCreatedTime());
        }
        return payOrderInfoDto;
    }


    /**
     * ????????????????????????
     *
     * @param orderInfoUuid
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @TxcTransaction
    public ResultRes<String> updateOrderPaySts(String orderInfoUuid) {
        log.info("---------????????????-------"+orderInfoUuid);
        if (StringUtils.isEmpty(orderInfoUuid)) {
            log.error("??????orderInfoUuid????????????");
            throw new BusinessException(ResEnum.LACK_PARAMETER);
        }
        boolean over= false;
        //??????????????????????????????
        String userName = TokenHelper.getUserName();
        //????????????id??????orderInfo??????
        OrderInfo orderInfo = this.selectOrderInfo(orderInfoUuid);
        //??????????????????????????????
        if (OrderTypeEnum.GOOD.getValue().equals(orderInfo.getOrderType())) {
            //????????????????????????
            OrderGoodsBranchAccount(orderInfo);

            //????????????????????????????????????
            this.updateGoodsOrder(orderInfo.getOrderUuid(), userName);
        } else if (OrderTypeEnum.CONSULT.getValue().equals(orderInfo.getOrderType())) {
            //????????????????????????
            consultOrderBranchAccount(orderInfo, OrderTypeEnum.CONSULT.getValue());

            //????????????????????????????????????
            this.updateConsultOrder(orderInfo.getOrderUuid(), userName,OrderTypeEnum.CONSULT.getValue());
        } else if (OrderTypeEnum.EXAMPLE.getValue().equals(orderInfo.getOrderType())) {
            //??????????????????
            caseOrderBranchAccount(orderInfo);
            //??????????????????????????????
            this.updateCaseOrder(orderInfo.getOrderUuid(), userName);
            over =true;
        } else if (OrderTypeEnum.AUDITOR.getValue().equals(orderInfo.getOrderType())) {
            //??????????????????
            consultOrderBranchAccount(orderInfo, OrderTypeEnum.AUDITOR.getValue());
            //??????????????????????????????
            this.updateConsultOrder(orderInfo.getOrderUuid(), userName,OrderTypeEnum.AUDITOR.getValue());
            over =true;
        } else if (OrderTypeEnum.DTC.getValue().equals(orderInfo.getOrderType())) {
            //dtc??????????????????
            dtcOrderBranchAccount(orderInfo);
            //??????dtc????????????????????????
            this.updateDtcOrder(orderInfo.getOrderUuid(), userName);
            over =true;
        } else if (OrderTypeEnum.COURSE.getValue().equals(orderInfo.getOrderType())) {
            //?????????????????????????????? ?????????
            this.updateCourseOrder(orderInfo.getOrderUuid(), OrderTypeEnum.COURSE.getValue(),userName);
            over =true;
        } else if (OrderTypeEnum.SCENE.getValue().equals(orderInfo.getOrderType())) {
            //?????????????????????????????????????????????
            this.updateSceneOrder(orderInfo.getOrderUuid(), userName,orderInfo.getOrderType());
           // sceneOrderBranchAccount(orderInfo);
            this.sceneOrderBranchAccount(orderInfo);
        } else if (OrderTypeEnum.SHARED_TECHNICIAN.getValue().equals(orderInfo.getOrderType())) {
            //????????????????????????
            shareTechnicianOrderBranchAccount(orderInfo);

            //????????????????????????????????????
            this.updateShareTechnicianOrder(orderInfo.getOrderUuid(), userName);
        }else if (OrderTypeEnum.SCENE_SERVICE.getValue().equals(orderInfo.getOrderType())) {
            //????????????????????????
            this.updateSceneOrder(orderInfo.getOrderUuid(), userName,orderInfo.getOrderType());

            //????????????????????????????????????
            this.sceneOrderBranchAccount(orderInfo);
        }
        //??????orderInfo??????????????????
        this.updateOrderInfo(orderInfoUuid, userName,over);
        return ResultRes.success(orderInfoUuid);
    }


    private void sceneOrderBranchAccount(OrderInfo orderInfo){
        SceneOrder sceneOrder = new SceneOrder();
        sceneOrder.setUuid(orderInfo.getOrderUuid());
        sceneOrder.setSts(StsEnum.ACTIVE.getValue());
        sceneOrder = sceneOrderMapper.selectOne(sceneOrder);
        if (null != sceneOrder) {
            this.addProfit(sceneOrder.getOrderNum(),OrderTypeEnum.SCENE.getValue(),sceneOrder.getTotalAmount(),sceneOrder.getIssuerUuid(),UserTypeEnum.vehicle.getType(),StreamTypeEnum.OUT.getType());
            this.addPlatfrom(sceneOrder.getOrderNum(), PlatformClassifyEnum.SERVICE.getValue(),orderInfo.getOrderType(),sceneOrder.getOrderServiceAmount());
            BigDecimal amt = sceneOrder.getTotalAmount().subtract(sceneOrder.getOrderServiceAmount());
            this.addPlatfrom(sceneOrder.getOrderNum(), PlatformClassifyEnum.ACCOUNT.getValue(),orderInfo.getOrderType(),amt);
        }

    }
    /**
     * ????????????????????????
     */
    private void shareTechnicianOrderBranchAccount(OrderInfo orderInfo) {
        //??????????????????????????????
        ShareTechnicianOrder shareTechnicianOrder = shareTechnicianOrderMapper.queryOrderShareTechnicianOrderInfo(orderInfo.getOrderUuid());
        if (null != shareTechnicianOrder) {
            //?????????????????????????????????
            this.addPlatfrom(shareTechnicianOrder.getOrderNum(), PlatformClassifyEnum.SERVICE.getValue(),orderInfo.getOrderType(),shareTechnicianOrder.getPlatformMoney());
            this.addPlatfrom(shareTechnicianOrder.getOrderNum(), PlatformClassifyEnum.ACCOUNT.getValue(),orderInfo.getOrderType(),shareTechnicianOrder.getReservationMoney());
            //????????????????????????
            //this.updateTechnicianAccount(shareTechnicianOrder.getTechnicianUuid(), shareTechnicianOrder.getReservationMoney());
           // this.addProfit(shareTechnicianOrder.getOrderNum(),orderInfo.getOrderType(),shareTechnicianOrder.getReservationMoney(),shareTechnicianOrder.getTechnicianUuid(),UserTypeEnum.technician.getType(),StreamTypeEnum.IN.getType());
            this.addProfit(shareTechnicianOrder.getOrderNum(),orderInfo.getOrderType(),shareTechnicianOrder.getPayNum(),shareTechnicianOrder.getOwnerUuid(),UserTypeEnum.vehicle.getType(),StreamTypeEnum.OUT.getType());
        }

    }

    /**
     * dtc?????????????????? ?????????
     *
     * @param orderInfo
     */
    private void dtcOrderBranchAccount(OrderInfo orderInfo) {
        //??????dtc??????????????????
        DtcOrder dtcOrderSelect = new DtcOrder();
        dtcOrderSelect.setSts(StsEnum.ACTIVE.getValue());
        dtcOrderSelect.setUuid(orderInfo.getOrderUuid());
        DtcOrder dtcOrder = dtcOrderMapper.selectOne(dtcOrderSelect);

        this.addPlatfrom(dtcOrder.getOrderNum(), PlatformClassifyEnum.FEE.getValue(),orderInfo.getOrderType(), dtcOrder.getOrderAmount());
        this.addProfit(dtcOrder.getOrderNum(), orderInfo.getOrderType(), dtcOrder.getOrderAmount(),dtcOrder.getBuyerUuid(),null,StreamTypeEnum.OUT.getType());
       /* if (null != dtcOrder) {
            if (UserTypeEnum.technician.getType().equals(dtcOrder.getDtcIssuerType())) {
                //????????????????????????
                this.updateTechnicianAccount(dtcOrder.getIssuerUuid(), dtcOrder.getOrderAmount());
            } else if (UserTypeEnum.store.getType().equals(dtcOrder.getDtcIssuerType())) {
                //????????????????????????
                this.updateStoreAccount(dtcOrder.getIssuerUuid(), dtcOrder.getOrderAmount(),dtcOrder.getOrderNum(),orderInfo.getOrderType());
            }
        }*/
    }

    /**
     * ??????????????????
     *
     * @param orderInfo
     */
    private void caseOrderBranchAccount(OrderInfo orderInfo) {
        //????????????????????????
        OrderCase orderCaseSelect = new OrderCase();
        orderCaseSelect.setUuid(orderInfo.getOrderUuid());
        orderCaseSelect.setSts(StsEnum.ACTIVE.getValue());
        OrderCase orderCase = orderCaseMapper.selectOne(orderCaseSelect);
        if (null != orderCase) {
            //??????????????????
            BigDecimal amount =orderCase.getReceivableAmount();
            BigDecimal casePlatform = queryPlatformCommission(Constants.CASE_COMMISSION);
            BigDecimal platformCommission = casePlatform.multiply(orderCase.getReceivableAmount()).setScale(2, BigDecimal.ROUND_HALF_UP);
            if (orderCase.getReceivableAmount().compareTo(platformCommission) == -1) {
                //????????????????????????
                this.updateTechnicianAccount(orderCase.getTechnicianUuid(), orderCase.getReceivableAmount());
            } else {
                //??????????????????????????????????????????????????? = ???????????? - ????????????
                amount = DigitUtils.subtract(orderCase.getReceivableAmount(), platformCommission);
                //????????????????????????
                this.updateTechnicianAccount(orderCase.getTechnicianUuid(), amount);
                this.addPlatfrom(orderCase.getOrderNum(),PlatformClassifyEnum.SERVICE.getValue(), orderInfo.getOrderType(), platformCommission);
            }
            //????????????
            this.addProfit(orderCase.getOrderNum(), orderInfo.getOrderType(), orderCase.getReceivableAmount(),orderCase.getCarOwnerUuid(),orderCase.getCarOwnerType(),StreamTypeEnum.OUT.getType());
            //????????????
            this.addProfit(orderCase.getOrderNum(), orderInfo.getOrderType(), amount,orderCase.getTechnicianUuid(),UserTypeEnum.technician.getType(),StreamTypeEnum.IN.getType());
        }
    }

    /**
     * ????????????/??????????????????
     */
    private void consultOrderBranchAccount(OrderInfo orderInfo, Integer orderType) {
        //??????????????????????????????
        ConsultOrder consultOrderSelect = new ConsultOrder();
        consultOrderSelect.setUuid(orderInfo.getOrderUuid());
        consultOrderSelect.setSts(StsEnum.ACTIVE.getValue());
        ConsultOrder consultOrder = consultOrderMapper.selectOne(consultOrderSelect);
        if (null != consultOrder) {
            Consult consultSelect = new Consult();
            consultSelect.setUuid(consultOrder.getConsultUuid());
            Consult consult = consultMapper.selectOne(consultSelect);
            if (null != consult && !StringUtils.isEmpty(consult.getTechnicianUuid())) {
                BigDecimal platformCommission = BigDecimal.ZERO;
                BigDecimal amount = consultOrder.getReceivableAmount();
                if (OrderTypeEnum.CONSULT.getValue().equals(orderType)) {
                    //????????????????????????
                    platformCommission = queryPlatformCommission(Constants.CONSULT_COMMISSION);
                    if (consultOrder.getReceivableAmount().compareTo(platformCommission) == -1) {
                        //????????????????????????
                        this.updateTechnicianAccount(consult.getTechnicianUuid(), consultOrder.getReceivableAmount());
                    } else {
                        //??????????????????????????????????????????????????? = ???????????? - ????????????
                        amount = DigitUtils.subtract(consultOrder.getReceivableAmount(), platformCommission);
                        //???????????????????????????
                        this.addPlatfrom(consultOrder.getOrderNum(), PlatformClassifyEnum.SERVICE.getValue() ,orderType, platformCommission);
                    }
                    //?????????????????? ???????????????????????????????????????????????????
                    this.addPlatfrom(consultOrder.getOrderNum(), PlatformClassifyEnum.ACCOUNT.getValue() ,OrderTypeEnum.ANSWER.getValue(), amount);
                } else if (OrderTypeEnum.AUDITOR.getValue().equals(orderType)) {
                    //????????????????????????
                    platformCommission = queryPlatformCommission(Constants.AUDIT_COMMISSION);
                    if (consultOrder.getReceivableAmount().compareTo(platformCommission) == -1) {
                        //????????????????????????
                        this.updateTechnicianAccount(consult.getTechnicianUuid(), consultOrder.getReceivableAmount());
                    } else {
                        //??????????????????????????????????????????????????? = ???????????? - ????????????
                        amount = DigitUtils.subtract(consultOrder.getReceivableAmount(), platformCommission);
                        //???????????????????????????
                        this.addPlatfrom(consultOrder.getOrderNum(), PlatformClassifyEnum.SERVICE.getValue() ,orderType, platformCommission);
                    }
                    this.addPlatfrom(consultOrder.getOrderNum(), PlatformClassifyEnum.FEE.getValue() ,orderType, amount);
                    //???????????????
                    /*this.addProfit(consultOrder.getOrderNum(), orderType, amount, consultOrder.getCarOwnerUuid(), consultOrder.getCarOwnerType(), StreamTypeEnum.IN.getType());
                    if(UserTypeEnum.technician.getType().equals(consultOrder.getCarOwnerType())) {
                        //????????????????????????
                        this.updateTechnicianAccount(consult.getCarOwnerUuid(), amount);
                    }else if(UserTypeEnum.store.getType().equals(consultOrder.getCarOwnerType())){
                        this.updateStoreAccount(consult.getCarOwnerUuid(),amount);
                    }else if(UserTypeEnum.vehicle.getType().equals(consultOrder.getCarOwnerType())){

                    }*/
                }

                //????????????
                this.addProfit(consultOrder.getOrderNum(), orderType,  consultOrder.getReceivableAmount(),consultOrder.getCarOwnerUuid(),consultOrder.getCarOwnerType(),StreamTypeEnum.OUT.getType());

            }
        }
    }

    /**
     * ??????????????????
     *
     * @param dictCode
     * @return
     */
    private BigDecimal queryPlatformCommission(String dictCode) {
        BigDecimal platformCommission = BigDecimal.ZERO;
        if (StringUtils.isEmpty(dictCode)) {
            return platformCommission;
        }
        //??????????????????
        ResultRes<DictionaryRes> resResultRes = systemFeign.queryByCode(dictCode);
        if (resResultRes.isSuccess()) {
            if (null != resResultRes.getData()) {
                platformCommission = new BigDecimal(resResultRes.getData().getLableDesc());
            }
        }
        return platformCommission;
    }

    /**
     * ????????????????????????
     * ???????????????????????????????????????
     * ?????????????????????????????????- ???????????????=??????????????????
     */
    private void OrderGoodsBranchAccount(OrderInfo orderInfo) {
        //??????????????????????????????
        OrderGoods orderGoodsSelect = new OrderGoods();
        orderGoodsSelect.setUuid(orderInfo.getOrderUuid());
        orderGoodsSelect.setSts(StsEnum.ACTIVE.getValue());
        OrderGoods orderGoods = orderGoodsMapper.selectOne(orderGoodsSelect);

        if (null != orderGoods) {
            //????????????
            BigDecimal amount = orderGoods.getActualAmount() == null ? BigDecimal.ZERO : orderGoods.getActualAmount();
            //????????????
            BigDecimal platformCommission = orderGoods.getPlatformServiceMoney() == null ? BigDecimal.ZERO : orderGoods.getPlatformServiceMoney();
            //????????????????????????????????????????????????????????????????????????????????????
            // if (checkGoodsShareStation(orderGoods.getGoodsUuid())) {
            //??????????????????????????????
//                platformCommission = queryPlatformCommission(Constants.SHARE_STATION_SERVICE_MONEY);
            if (amount.compareTo(platformCommission) == -1) {
                amount = orderGoods.getActualAmount();
            } else {
                amount = DigitUtils.subtract(amount, platformCommission);
                addPlatfrom(orderGoods.getOrderNum(), PlatformClassifyEnum.SERVICE.getValue(), orderInfo.getOrderType(), platformCommission);
            }
            addPlatfrom(orderGoods.getOrderNum(), PlatformClassifyEnum.ACCOUNT.getValue(),orderInfo.getOrderType(), amount);

            addProfit(orderGoods.getOrderNum(), orderInfo.getOrderType(),orderGoods.getActualAmount() , orderGoods.getUserUuid(), UserTypeEnum.vehicle.getType(), StreamTypeEnum.OUT.getType());



        }
    }

    /**
     * ???????????????????????????????????????
     *
     * @param goodsUuid
     * @return
     */
    private Boolean checkGoodsShareStation(String goodsUuid) {
        Boolean flag = false;
        if (StringUtils.isEmpty(goodsUuid)) {
            return flag;
        }
        //??????????????????
        ResultRes<GoodsRes> goodsResResultRes = goodsFeign.queryGoodsDetail(goodsUuid);
        if (goodsResResultRes.isSuccess()) {
            if (null != goodsResResultRes.getData()) {
                if (Constants.SHARE_STATION_UUID.equals(goodsResResultRes.getData().getLevelTwoUuid())) {
                    flag = true;
                }

            }
        }
        return flag;
    }

    /**
     * ????????????id??????orderInfo??????
     */
    private OrderInfo selectOrderInfo(String orderInfoUuid) {
        OrderInfo orderInfo = orderInfoMapper.queryOrderInfo(orderInfoUuid);
        if (orderInfo == null) {
            log.error("???????????????????????????orderInfo?????????,??????id??????{}", orderInfoUuid);
            throw new BusinessException(ResEnum.NON_EXISTENT);
        }
        return orderInfo;
    }

    /**
     * ??????orderInfo????????????????????????
     */
    private void updateOrderInfo(String orderInfoUuid, String userName,boolean over) {
        OrderInfo orderInfoUpdate = new OrderInfo();
        orderInfoUpdate.setOrderUuid(orderInfoUuid);
        //true??????????????????????????????
        if( over ) {
            orderInfoUpdate.setPaySts(OrderStsEnum.COMPLETED.getValue());
        }else {
            orderInfoUpdate.setPaySts(OrderStsEnum.HAVE_PAID.getValue());
        }
        orderInfoUpdate.setLastUpdatedBy(userName);
        orderInfoUpdate.setLastUpdatedTime(new Date());
        int orderInfoUpdateNum = orderInfoMapper.updateRefund(orderInfoUpdate);
        if (orderInfoUpdateNum <= 0) {
            log.error("??????orderInfo???????????????????????????????????????????????????{}", JSON.toJSONString(orderInfoUpdate));
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }
    }

    /**
     * ??????????????????????????????
     */
    private void updateGoodsOrder(String goodsOrderUuid, String userName) {
        OrderGoods orderGoods = new OrderGoods();
        orderGoods.setUuid(goodsOrderUuid);
        orderGoods.setOrderSts(OrderStsEnum.HAVE_PAID.getValue());
        orderGoods.setLastUpdatedBy(userName);
        orderGoods.setLastUpdatedTime(new Date());
        int updateOrderGoodsNum = orderGoodsMapper.updateByPrimaryKeySelective(orderGoods);
        if (updateOrderGoodsNum <= 0) {
            log.error("?????????????????????????????????????????????????????????????????????{} ", JSON.toJSONString(orderGoods));
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }
    }

    /**
     * ??????????????????/????????????????????????
     */
    private void updateConsultOrder(String consultOrderUuid, String userName,Integer orderType) {
        ConsultOrder consultOrder = consultOrderMapper.selectByPrimaryKey(consultOrderUuid);
        consultOrder.setOrderSts(OrderStsEnum.HAVE_PAID.getValue());
        consultOrder.setLastUpdatedBy(userName);
        consultOrder.setLastUpdatedTime(new Date());
        int updateOrderGoodsNum = consultOrderMapper.updateByPrimaryKeySelective(consultOrder);
        if (updateOrderGoodsNum <= 0) {
            log.error("??????????????????/???????????????????????????????????????????????????{} ", JSON.toJSONString(consultOrder));
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }
        //?????????????????????????????????????????????????????????
        if(OrderTypeEnum.CONSULT.getValue().equals(orderType)) {
            orderConsultService.pushContentCheck(consultOrderUuid);
        }
    }

    /**
     * ????????????????????????
     */
    private void updateCaseOrder(String caseOrderUuid, String userName) {
        OrderCase orderCase = new OrderCase();
        orderCase.setUuid(caseOrderUuid);
        orderCase.setOrderSts(OrderStsEnum.COMPLETED.getValue());
        orderCase.setLastUpdatedBy(userName);
        orderCase.setLastUpdatedTime(new Date());
        int updateOrderGoodsNum = orderCaseMapper.updateByPrimaryKeySelective(orderCase);
        if (updateOrderGoodsNum <= 0) {
            log.error("?????????????????????????????????????????????????????????????????????{} ", JSON.toJSONString(orderCase));
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }
    }

    /**
     * ??????DTC????????????
     */
    private void updateDtcOrder(String dtcOrderUuid, String userName) {
        DtcOrder dtcOrder = new DtcOrder();
        dtcOrder.setUuid(dtcOrderUuid);
        dtcOrder.setOrderSts(OrderStsEnum.COMPLETED.getValue());
        dtcOrder.setLastUpdatedBy(userName);
        dtcOrder.setLastUpdatedTime(new Date());
        int updateOrderDtcNum = dtcOrderMapper.updateByPrimaryKeySelective(dtcOrder);
        if (updateOrderDtcNum <= 0) {
            log.error("??????Dtc???????????????????????????????????????????????????{} ", JSON.toJSONString(dtcOrder));
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }

    }

    /**
     * ?????????????????????????????? ?????????
     */
    private void updateCourseOrder(String courseOrderUuid,Integer orderType , String userName) {
        //????????????????????????
        CourseOrder courseOrderSelect = new CourseOrder();
        courseOrderSelect.setUuid(courseOrderUuid);
        CourseOrder courseOrderInfo = courseOrderMapper.selectOne(courseOrderSelect);
        if (null != courseOrderInfo) {
            Course courseSelect = new Course();
            courseSelect.setUuid(courseOrderInfo.getUuid());
            Course course = courseMapper.selectOne(courseSelect);
            if (null != course) {
                Course updateCourse = new Course();
                updateCourse.setUuid(course.getUuid());
                int courseSalesVolume = null == course.getCourseSalesVolume() ? 0 : course.getCourseSalesVolume();
                updateCourse.setCourseSalesVolume(courseSalesVolume + 1);
                updateCourse.setLastUpdatedTime(new Date());
                updateCourse.setLastUpdatedBy(userName);
                int updateCourseNum = courseMapper.updateByPrimaryKeySelective(updateCourse);
                if (updateCourseNum <= 0) {
                    log.error("???????????????????????????????????????????????????{} ", JSON.toJSONString(updateCourse));
                    throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
                }
            }
        }
        CourseOrder courseOrder = new CourseOrder();
        courseOrder.setUuid(courseOrderUuid);
        courseOrder.setOrderSts(OrderStsEnum.COMPLETED.getValue());
        courseOrder.setLastUpdatedBy(userName);
        courseOrder.setLastUpdatedTime(new Date());
        int updateOrderDtcNum = courseOrderMapper.updateByPrimaryKeySelective(courseOrder);
        if (updateOrderDtcNum <= 0) {
            log.error("???????????????????????????????????????????????????????????????{} ", JSON.toJSONString(courseOrder));
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }
        this.addPlatfrom(courseOrder.getOrderNum(),PlatformClassifyEnum.FEE.getValue(),orderType,courseOrder.getOrderAmount());
        this.addProfit(courseOrder.getOrderNum(),orderType,courseOrder.getOrderAmount(),courseOrder.getBuyerUuid(),null,StreamTypeEnum.OUT.getType());
    }

    /**
     * ????????????????????????????????????
     *
     * @param sceneOrderUuid
     * @param userName
     */
    private void updateSceneOrder(String sceneOrderUuid, String userName,Integer orderType) {
        SceneOrder sceneOrder = new SceneOrder();
        sceneOrder.setUuid(sceneOrderUuid);
        if(orderType.equals(OrderTypeEnum.SCENE.getValue())) {
            sceneOrder.setOrderSts(SceneOrderStsEnum.WAIT_DOOR.getValue());
        }else if(orderType.equals(OrderTypeEnum.SCENE_SERVICE.getValue())) {
            sceneOrder.setOrderSts(SceneOrderStsEnum.IN_SERVICE.getValue());
        }
        sceneOrder.setLastUpdatedBy(userName);
        sceneOrder.setLastUpdatedTime(new Date());
        int updateNum = sceneOrderMapper.updateByPrimaryKeySelective(sceneOrder);
        if (updateNum <= 0) {
            log.error("?????????????????????????????????????????????????????????????????????{} ", JSON.toJSONString(sceneOrder));
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }

    }

    /**
     * ????????????????????????????????????
     *
     * @param shareTechnicianOrderUuid
     * @param userName
     */
    private void updateShareTechnicianOrder(String shareTechnicianOrderUuid, String userName) {
        ShareTechnicianOrder shareTechnicianOrder = new ShareTechnicianOrder();
        shareTechnicianOrder.setUuid(shareTechnicianOrderUuid);
        shareTechnicianOrder.setOrderStatus(ShareTechnicianOrderEnum.PendingOrder.getValue());
        shareTechnicianOrder.setLastUpdatedBy(userName);
        shareTechnicianOrder.setLastUpdatedTime(new Date());
        int updateNum = shareTechnicianOrderMapper.updateByPrimaryKeySelective(shareTechnicianOrder);
        if (updateNum <= 0) {
            log.error("?????????????????????????????????????????????????????????????????????{} ", JSON.toJSONString(shareTechnicianOrder));
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }
    }

    /**
     * ????????????????????????
     *
     * @param storeUuid
     * @param orderAccount
     */
    private void updateStoreAccount(String storeUuid, BigDecimal orderAccount) {
        UpdateStoreAccountReq req = new UpdateStoreAccountReq();
        req.setStoreUuid(storeUuid);
        req.setOrderAmount(orderAccount);
        ResultRes<String> resultRes = storeFegin.updateStoreAccount(req);
        if (!resultRes.isSuccess()) {
            log.error("?????????????????????????????????????????????{}", JSON.toJSONString(req));
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }


    }


    private void addPlatfrom(String orderNo, Integer classify ,Integer orderType, BigDecimal amt) {
        PlatformStreamReq platformStreamReq = new PlatformStreamReq();
        platformStreamReq.setAmt(amt);
        platformStreamReq.setClassify(classify);
        platformStreamReq.setOrderNo(orderNo);
        platformStreamReq.setOrderType(orderType);
        platformStreamReq.setStreamType(StreamTypeEnum.IN.getType());
        platformStreamFeign.addPlatfrom(platformStreamReq);
    }

    /**
     * ??????????????????
     * @param orderNo
     * @param orderType
     * @param amt
     * @param userUid
     * @param userType
     * @param streamType
     */
    private void addProfit(String orderNo, Integer orderType, BigDecimal amt,String userUid,Integer userType,Integer streamType) {
        AddProfitReq addProfitReq = new AddProfitReq();
        addProfitReq.setAmt(amt);
        addProfitReq.setUserType(userType);
        addProfitReq.setUserUuid(userUid);
        addProfitReq.setClassify(orderType);
        addProfitReq.setOrderNo(orderNo);
        addProfitReq.setStreamType(streamType);
        profitStreamFeign.addProfit(addProfitReq);
    }


    /**
     * ????????????????????????
     *
     * @param technicianUuid
     * @param orderAccount
     */
    private void updateTechnicianAccount(String technicianUuid, BigDecimal orderAccount) {
        UpdateTechnicianAccountReq req = new UpdateTechnicianAccountReq();
        req.setTechnicianUuid(technicianUuid);
        req.setOrderAmount(orderAccount);
        ResultRes<String> resultRes = technicianFegin.updateTechnicianAccount(req);
        if (!resultRes.isSuccess()) {
            log.error("?????????????????????????????????????????????{}", JSON.toJSONString(req));
            throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
        }
    }

}
