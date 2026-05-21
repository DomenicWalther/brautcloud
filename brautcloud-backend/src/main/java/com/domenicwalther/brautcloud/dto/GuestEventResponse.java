package com.domenicwalther.brautcloud.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class GuestEventResponse {

	private String eventName;

	private String firstNameCoupleOne;

	private String firstNameCoupleTwo;

}
