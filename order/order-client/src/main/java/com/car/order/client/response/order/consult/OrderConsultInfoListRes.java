package com.car.order.client.response.order.consult;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @author zhouz
 * @date 2020/12/31
 */
@Data
@ApiModel(value = "OrderConsultInfoListRes", description = "咨询订单列表信息VO")
public class OrderConsultInfoListRes {

	@ApiModelProperty(value = "uuid", name = "uuid")
	private String uuid;

	@ApiModelProperty(value = "咨询标题", name = "title")
	private String title;

	@ApiModelProperty(value = "车主姓名", name = "carOwnerName")
	private String carOwnerName;

	@ApiModelProperty(value = "车主手机号", name = "carOwnerMobile")
	private String carOwnerMobile;

	@ApiModelProperty(value = "订单开始时间", name = "createdTime")
	private String createdTime;

	@ApiModelProperty(value = "技师姓名", name = "technicianName")
	private String technicianName;

	@ApiModelProperty(value = "技师手机号", name = "technicianMobile")
	private String technicianMobile;

	@ApiModelProperty(value = "订单类型 0 订单点评  1 咨询 2 回答 3 案例 4 旁听", name = "orderType")
	private Integer orderType;

	@ApiModelProperty(value = "订单金额", name = "orderAmount")
	private BigDecimal orderAmount;

	@ApiModelProperty(value = "支付方式 0 微信支付 1 支付宝支付", name = "payType")
	private Integer payType;

	@ApiModelProperty(value = "订单状态 0 待支付 1 已支付 2: 已取消 3:退款中  4:退款成功  5:退款失败", name = "orderSts")
	private Integer orderSts;

	@ApiModelProperty(value = "答复状态 0 未答复 1 已答复", name = "answerSts")
	private Integer answerSts;

	@ApiModelProperty(value = "车辆品牌", name = "vehicleBrand")
	private String vehicleBrand;

	@ApiModelProperty(value = "车型类型", name = "vehicleModel")
	private String vehicleModel;

}
