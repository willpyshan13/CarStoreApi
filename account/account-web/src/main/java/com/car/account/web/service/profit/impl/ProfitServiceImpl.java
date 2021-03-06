package com.car.account.web.service.profit.impl;

import com.car.account.client.request.profit.AddProfitReq;
import com.car.account.client.response.account.AccountRes;
import com.car.account.client.response.account.QueryQuizCaseCarCountRes;
import com.car.account.client.response.profit.AccountAmtRes;
import com.car.account.client.response.profit.sub.ProfitStreamClassify;
import com.car.account.web.dto.profit.ClassifyProfitDto;
import com.car.account.web.mapper.profit.ProfitStreamMapper;
import com.car.account.web.mapper.store.StoreAccountMapper;
import com.car.account.web.mapper.technician.TechnicianAccountMapper;
import com.car.account.web.mapper.vehicle.VehicleMapper;
import com.car.account.web.model.profit.ProfitStream;
import com.car.account.web.model.store.StoreAccount;
import com.car.account.web.model.technician.TechnicianAccount;
import com.car.account.web.service.profit.ProfitService;
import com.car.common.enums.*;
import com.car.common.exception.BusinessException;
import com.car.common.res.ResultRes;
import com.car.common.utils.DigitUtils;
import com.car.common.utils.TokenHelper;
import com.car.common.utils.UuidUtils;
import com.car.order.client.feign.*;
import com.car.order.client.request.order.consult.QueryOrderConsultFrontListReq;
import com.car.order.client.request.order.goods.QueryOrderGoodsFrontListReq;
import com.car.order.client.request.order.instance.QueryOrderCaseFrontListReq;
import com.car.order.client.request.technicianappointment.QueryShareTechnicianOrderReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author zhangyp
 * @date 2021/1/27 22:25
 */
@Slf4j
@Service
public class ProfitServiceImpl implements ProfitService {

    @Resource
    private StoreAccountMapper storeAccountMapper;
    @Resource
    private TechnicianAccountMapper technicianAccountMapper;
    @Resource
    private ProfitStreamMapper profitStreamMapper;

    @Autowired
    private OrderFrontFeign orderFrontFeign;

    @Autowired
    private VehicleMapper vehicleMapper;

    @Autowired
    OrderCaseFrontFeign orderCaseFrontFeign;

    @Autowired
    OrderConsultFrontFeign orderConsultFrontFeign;

    @Autowired
    OrderGoodsFrontFeign orderGoodsFrontFeign;

    @Autowired
    ShareTechnicianFrontFeign shareTechnicianFrontFeign;


    @Override
    public ResultRes<AccountRes> queryAccount() {

        String userUuid = TokenHelper.getUserUuid();
        Integer userType = TokenHelper.getUserType();
        AccountRes accountRes = initAccount(userUuid, userType);
        return ResultRes.success(accountRes);
    }

    @Override
    public ResultRes<AccountAmtRes> queryProfitClassify() {

        String userUuid = TokenHelper.getUserUuid();
        Integer userType = TokenHelper.getUserType();
        AccountRes accountRes = initAccount(userUuid, userType);
        AccountAmtRes rst = new AccountAmtRes();
        if(null != accountRes){
            BeanUtils.copyProperties(accountRes,rst);
        }

        //??????????????????
        List<ClassifyProfitDto> inList = profitStreamMapper.staticsClassifyProfitAmt(userUuid, userType, StreamTypeEnum.IN.getType());
        //??????????????????
        List<ClassifyProfitDto> otList = profitStreamMapper.staticsClassifyProfitAmt(userUuid, userType, StreamTypeEnum.OUT.getType());

        List<ProfitStreamClassify> psList = new ArrayList<>();
        for(ClassifyEnum f : ClassifyEnum.values()){

            Integer type = f.getType();

            ProfitStreamClassify c = new ProfitStreamClassify();
            c.setClassify(type);
            c.setAmt(BigDecimal.ZERO);
            c.setWithdrawAmt(BigDecimal.ZERO);

            if(!CollectionUtils.isEmpty(inList)){
                inList.stream().forEach(s ->{

                    if(f.getType().equals(s.getClassify())){
                        c.setAmt(s.getTotalAmt());
                    }
                });
            }
            if(!CollectionUtils.isEmpty(otList)){
                otList.stream().forEach(s ->{

                    if(f.getType().equals(s.getClassify())){
                        c.setWithdrawAmt(s.getTotalAmt());
                    }
                });
            }
            psList.add(c);
        }
        rst.setProfitStreamClassifies(psList);
        return ResultRes.success(rst);
    }

    /**
     * ??????????????????????????????????????????
     */
    @Override
    public ResultRes<QueryQuizCaseCarCountRes> queryQuizCaseCarCount() {
        //??????uuid
        String userUuid = TokenHelper.getUserUuid();
        QueryQuizCaseCarCountRes queryQuizCaseCarCountRes = new QueryQuizCaseCarCountRes();
        //????????????????????????
        ResultRes<Integer> quizResultRes = orderFrontFeign.queryQuizCount(userUuid);
        if (quizResultRes.isSuccess()) {
            queryQuizCaseCarCountRes.setQuizCount(quizResultRes.getData());
        } else {
            queryQuizCaseCarCountRes.setQuizCount(0);
        }
        //????????????????????????
        ResultRes<Integer> caseResultRes = orderFrontFeign.queryCaseCount(userUuid);
        if (caseResultRes.isSuccess()) {
            queryQuizCaseCarCountRes.setCaseCount(caseResultRes.getData());
        } else {
            queryQuizCaseCarCountRes.setCaseCount(0);
        }
        //????????????????????????
        Integer vehicleCount = vehicleMapper.queryVehicleCount(userUuid);
        queryQuizCaseCarCountRes.setCarCount(vehicleCount);
        return ResultRes.success(queryQuizCaseCarCountRes);
    }

    @Override
    public ResultRes<String> addProfit(AddProfitReq addProfitReq) {
        ProfitStream profitStream = new ProfitStream();
        BeanUtils.copyProperties(addProfitReq,profitStream);
        profitStream.setUuid(UuidUtils.getUuid());
        profitStream.setCreatedTime(new Date());
        profitStream.setSts(StsEnum.ACTIVE.getValue());
        profitStreamMapper.insert(profitStream);
        return ResultRes.success(profitStream.getUuid());
    }

    /**
     * ???????????????????????????
     * @param userUuid
     * @param userType
     * @return
     */
    private AccountRes initAccount(String userUuid,Integer userType){
        /**
         * ???????????? :   ?????????????????????????????????
         * ??????????????? : ?????????????????????
         * ??????????????? : ???????????? - ???????????? - ???????????????
         */
        AccountRes rst = new AccountRes();
        if (UserTypeEnum.technician.getType().equals(userType)){

            log.info("??????????????????????????????");
            TechnicianAccount t = new TechnicianAccount();
            t.setSts(StsEnum.ACTIVE.getValue());
            t.setTechnicianUuid(userUuid);
            TechnicianAccount account = technicianAccountMapper.selectOne(t);
//            if(null == account){
//
//                log.error("??????????????????????????????");
//                throw new BusinessException(ResEnum.TECHNICIAN_NO_ACCOUNT_AMT);
//            }
            if (!StringUtils.isEmpty(account)) {
                //????????????
                BigDecimal accountAmount = initVal(account.getAccountAmount());
                //???????????????
                BigDecimal withdrawAmount = initVal(account.getWithdrawAmount());
                //????????????
                BigDecimal frozenAmt = initVal(account.getFrozenAmt());
                //?????????????????????
                BigDecimal ingAmt = queryIngAmt(userUuid, UserTypeEnum.technician);
                BigDecimal aviWithdrawAmt = DigitUtils.subtract(accountAmount,frozenAmt,ingAmt);

                rst.setAccountAmt(accountAmount);
                rst.setAviWithdrawAmt(aviWithdrawAmt);
                rst.setWithdrawAmt(withdrawAmount);
                rst.setIngAmt(ingAmt);
            }

        }else if(UserTypeEnum.store.getType().equals(userType)){


            log.info("??????????????????????????????");
            StoreAccount sa = new StoreAccount();
            sa.setSts(StsEnum.ACTIVE.getValue());
            sa.setStoreUuid(userUuid);
            StoreAccount storeAccount = storeAccountMapper.selectOne(sa);
//            if(null == storeAccount){
//
//                log.error("????????????????????????????????????.storeUuid:{}",userUuid);
//                throw new BusinessException(ResEnum.STORE_NO_ACCOUNT_AMT);
//            }

            if (!StringUtils.isEmpty(storeAccount)) {
                BigDecimal accountAmount = initVal(storeAccount.getAccountAmount());
                BigDecimal withdrawAmount = initVal(storeAccount.getWithdrawAmount());
                BigDecimal frozenAmt = initVal(storeAccount.getFrozenAmt());
                BigDecimal ingAmt = queryIngAmt(userUuid, UserTypeEnum.technician);
                BigDecimal aviWithdrawAmt = DigitUtils.subtract(accountAmount,frozenAmt,ingAmt);

                rst.setAccountAmt(accountAmount);
                rst.setAviWithdrawAmt(aviWithdrawAmt);
                rst.setWithdrawAmt(withdrawAmount);
                rst.setIngAmt(ingAmt);
            }

        }else if(UserTypeEnum.vehicle.getType().equals(userType)){

            log.error("?????????????????????????????????????????????");
            throw new BusinessException(ResEnum.VEHICLE_NOT_ACCOUNT_AMT);
        }else {

            log.error("??????????????????>>>userType:{}",userType);
            throw new BusinessException(ResEnum.VEHICLE_NOT_ACCOUNT_AMT);
        }

        int orderNum = queryOrderNum(userUuid, userType);
        rst.setOrderNum(orderNum);
        return rst;
    }


    private BigDecimal initVal(BigDecimal s){

        return (null == s) ? BigDecimal.ZERO : s;
    }
    /**
     * ?????????????????????
     * @param userUuid
     * @param userTypeEnum
     * @return
     */
    private BigDecimal queryIngAmt(String userUuid,UserTypeEnum userTypeEnum){
        //TODO ?????????
        return BigDecimal.ZERO;
    }

    /**
     * ?????????????????? TODO ???????????????
     * @param userUuid
     * @param userTypeEnum
     * @return
     */
    private int queryOrderNum(String userUuid, Integer userTypeEnum) {
        QueryShareTechnicianOrderReq req =new QueryShareTechnicianOrderReq();
        Integer shar = shareTechnicianFrontFeign.queryShareTechnicianOrderList(req).getTotal();
        QueryOrderCaseFrontListReq param = new QueryOrderCaseFrontListReq();
        Integer caseO = orderCaseFrontFeign.queryOrderCaseList(param).getTotal();
        QueryOrderConsultFrontListReq param1 =new QueryOrderConsultFrontListReq();
        Integer consult = orderConsultFrontFeign.queryOrderConsultList(param1).getTotal();
        QueryOrderGoodsFrontListReq queryStoreListReq = new QueryOrderGoodsFrontListReq();
        Integer goods = orderGoodsFrontFeign.queryOrderGoodsList(queryStoreListReq).getTotal();
        Integer count = shar+caseO+consult+goods;
        return count;
    }
}
