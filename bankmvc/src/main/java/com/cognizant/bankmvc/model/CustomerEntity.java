package com.cognizant.bankmvc.model;


import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomerEntity {
	private String userid;
	
	private String username;
	
	@NotBlank
	@Pattern(regexp="(?=^.{8,}$)((?=.*\\d)|(?=.*\\W+))(?![.\\n])(?=.*[A-Z])(?=.*[a-z]).*$" ,message="Enter a Strong Password")
	private String password;
	
	private Date dateOfBirth;
	
	@Pattern(regexp = "^[A-Z]{5}+[0-9]{4}+[A-Z]{1}$",message="Enter Valid PAN Number")
	@NotBlank
	private String pan;
	
	@NotBlank
	private String address;
	
	private List<Account> accounts = new ArrayList<Account>();
	
}
