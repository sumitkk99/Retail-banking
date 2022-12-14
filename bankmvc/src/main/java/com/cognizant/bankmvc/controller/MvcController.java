package com.cognizant.bankmvc.controller;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.cognizant.bankmvc.exception.CustomerNotFoundException;
import com.cognizant.bankmvc.feign.AccountFeign;
import com.cognizant.bankmvc.feign.AuthenticationFeign;
import com.cognizant.bankmvc.feign.CustomerFeign;
import com.cognizant.bankmvc.feign.RulesFeign;
import com.cognizant.bankmvc.feign.TransactionFeign;
import com.cognizant.bankmvc.model.Account;
import com.cognizant.bankmvc.model.AccountInput;
import com.cognizant.bankmvc.model.AppUser;
import com.cognizant.bankmvc.model.CustomerEntity;
import com.cognizant.bankmvc.model.Transaction;
import com.cognizant.bankmvc.model.TransactionInput;

//import jdk.internal.org.jline.utils.Log;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@RestController
public class MvcController  {
	
	
	HttpSession session;

	@Autowired
	private AuthenticationFeign authFeign;

	@Autowired
	private CustomerFeign customerFeign;

	@Autowired
	private AccountFeign accountFeign;

	@Autowired
	private TransactionFeign transactionFeign;
	
	@Autowired
	private RulesFeign rulesFeign;
	
	@GetMapping("/error")
	public String error()
	{
		return "ERROR";
	}
	
	/**
	 * @param request
	 * @param model
	 * @return ModelAndView
	 */
	@RequestMapping("/")
	public ModelAndView home(HttpServletRequest request , Model model) {
		try{		
			request.getSession(false);
			String role = (String) session.getAttribute("role");
			if(role.equalsIgnoreCase("EMPLOYEE"))
			{
				return new ModelAndView("redirect:/dashboard");
			}
			else
			{
				return new ModelAndView("redirect:/customerdashboard");
			}
		}
		catch(Exception e)
		{
			return new ModelAndView("home");			
		}
	}

	/**
	 * @param msg
	 * @param model
	 * @return ModelAndView
	 */
	@GetMapping("/employeelogin")
	public ModelAndView employeeLogin(@RequestParam(defaultValue = "") String msg, Model model) {
		model.addAttribute("role", "EMPLOYEE");
		model.addAttribute("msg", msg);
		model.addAttribute("login", new AppUser());
		return new ModelAndView("login");
	}

	/**
	 * @param msg
	 * @param model
	 * @return ModelAndView
	 */
	@GetMapping("/customerlogin")
	public ModelAndView customerLogin(@RequestParam(defaultValue = "") String msg,Model model) {
		model.addAttribute("role", "CUSTOMER");
		model.addAttribute("msg", msg);
		model.addAttribute("login", new AppUser());
		return new ModelAndView("login");
	}

	/**
	 * @param user
	 * @param request
	 * @param model
	 * @return ModelAndView
	 * @throws CustomerNotFoundException
	 */
	@PostMapping("/login")
	public ModelAndView login(@ModelAttribute("login") AppUser user, HttpServletRequest request, Model model) throws CustomerNotFoundException {
		session = request.getSession();
		String token = "Bearer ";
		AppUser loginUser = null;
		try {
			loginUser = authFeign.login(user);
		} catch (Exception ex) {
			String dir = "";
			if (user.getRole().equalsIgnoreCase("EMPLOYEE")) {
				dir = "employeelogin";
			} else {
				dir = "customerlogin";
			}
			return new ModelAndView("redirect:/" + dir + "?msg=Invalid Credentials");
		}
		token += loginUser.getAuthToken();
		
		session.setAttribute("userId", loginUser.getUserid());
		session.setAttribute("token", token);
		if (user.getRole().equalsIgnoreCase("CUSTOMER")) {
			CustomerEntity customer = customerFeign.getCustomerDetails(token, loginUser.getUserid());
			System.out.println(customer);
			model.addAttribute("customer", customer);
			model.addAttribute("accountinput", new AccountInput());
			return new ModelAndView("redirect:/customerdashboard");
		}
		return new ModelAndView("redirect:/dashboard");

	}

	/**
	 * @return ModelAndView
	 */
	@GetMapping("/logoutPage")
	public ModelAndView logoutPage() {
		return new ModelAndView("logoutPage");
	}

	/**
	 * @param request
	 * @return ModelAndView
	 */
	@GetMapping("/logout")
	public ModelAndView logout(HttpServletRequest request) {
		try{		
			request.getSession(false);
			session.removeAttribute("token");
			session.removeAttribute("userId");
			session.invalidate();		
			return new ModelAndView("home");
		}
		catch(Exception e)
		{
			return new ModelAndView("home");
		}
	}

	/**
	 * @param request
	 * @param msg
	 * @param model
	 * @return ModelAndView
	 */
	@GetMapping("/createCustomer")
	public ModelAndView createCustomer(HttpServletRequest request,@RequestParam(defaultValue = "") String msg,Model model) {
		try{		
			request.getSession(false);
			model.addAttribute("role", "EMPLOYEE");
			model.addAttribute("msg", msg);
			model.addAttribute("customer", new CustomerEntity());
			return new ModelAndView("createcustomer");
		}
		catch(Exception e)
		{
			return new ModelAndView("home");
		}
	}

	/**
	 * @param request
	 * @param model
	 * @return ModelAndView
	 */
	@GetMapping("/viewCustomer")
	public ModelAndView viewCustomer(HttpServletRequest request, Model model) {
		model.addAttribute("role", "EMPLOYEE");
		try {
			String id = request.getParameter("userId");
			String token = (String) session.getAttribute("token");
			System.out.println(token);
			CustomerEntity customer = customerFeign.getCustomerDetails(token, id);
			System.out.println(customer);
			model.addAttribute("customer", customer);
			return new ModelAndView("viewcustomer");
		} catch (Exception ex) {

			return new ModelAndView("redirect:/dashboard?viewmsg=Invalid CustomerID");
		}
	}

	/**
	 * @param request
	 * @param model
	 * @param redirectAttributes
	 * @return ModelAndView
	 */
	@PostMapping("/createAccount")
	public ModelAndView createAccount(HttpServletRequest request, Model model, @RequestParam(defaultValue = "") String msg,RedirectAttributes redirectAttributes) {
		try {
			String custid = request.getParameter("customerId");
			String token = (String) session.getAttribute("token");
			customerFeign.getCustomerDetails(token, custid);
			model.addAttribute("role", "EMPLOYEE");
			model.addAttribute("customerId", custid);
			Account account = new Account();
			long accountId = account.getAccountId();
			model.addAttribute("accountId", accountId);
			model.addAttribute("account", account);
			return new ModelAndView("createaccount");			
		}
		catch(Exception e)
		{
			return new ModelAndView("redirect:/dashboard?accmsg=Invalid CustomerID");
		}
	}

	/**
	 * @param model
	 * @return ModelAndView
	 */
	@GetMapping("/403")
	public ModelAndView errorpage( Model model) {
		return new ModelAndView("403");
	}

	/**
	 * @param customer
	 * @param model
	 * @param redirectAttributes
	 * @return ModelAndView
	 * @throws MethodArgumentNotValidException
	 */
	@PostMapping("/finishedCustomerCreation")
	 public ModelAndView createAccountAndAccount(@Valid @ModelAttribute("customer") CustomerEntity customer,BindingResult result, Model model,
			RedirectAttributes redirectAttributes) throws MethodArgumentNotValidException {
		model.addAttribute("role", "EMPLOYEE");
		log.info("submitted customer details");
		try {
			if(result.hasErrors())
			{
				log.info("error");
				return new ModelAndView("createcustomer");
				
			}
			String token = (String) session.getAttribute("token");
			customerFeign.saveCustomer(token, customer);
			model.addAttribute("role", "EMPLOYEE");
			model.addAttribute("customerId", customer.getUserid());
			model.addAttribute("account", new Account());
			return new ModelAndView("createaccount");
	}
	catch (Exception ex) {
		 
		return new ModelAndView("redirect:/createCustomer?msg=Enter the Valid Account Number");
	}
		
	}

	/**
	 * @param account
	 * @param model
	 * @return ModelAndView
	 */
	@PostMapping("/finishedAccountCreation")
	public ModelAndView createAccount(@ModelAttribute("account") Account account, Model model) {
		model.addAttribute("role", "EMPLOYEE");
		CustomerEntity customer = null;
		try {
			String token = (String) session.getAttribute("token");
			customer = customerFeign.getCustomerDetails(token, account.getCustomerId());
		} catch (Exception ex) {
			return new ModelAndView("redirect:/createAccount?msg=Invalid CustomerID");
		}
		System.out.println(customer);
		System.out.println("Customer Entity" + customer + "\nAccount" + account);
		List<Account> list = new ArrayList<Account>();
		list.add(account);
		customer.setAccounts(list);
		try {
			String token = (String) session.getAttribute("token");
			System.out.println("Ready to create");
			accountFeign.createAccount(token, account.getCustomerId(), account);
		} catch (Exception ex) {
			log.error("Failed to create");
			model.addAttribute("accmsg","Account Not created");
			return new ModelAndView("createaccount");
		}
		model.addAttribute("accmsg","Account created Successfully");
		return new ModelAndView("createaccount");
	}

	/**
	 * @param request
	 * @return ModelAndView
	 */
	@PostMapping("/deposit")
	public ModelAndView deposit(HttpServletRequest request) {
		long accountId = Long.parseLong(request.getParameter("accountId"));
		double amount = Double.parseDouble(request.getParameter("amount"));
		AccountInput input = new AccountInput(accountId, amount);

		try {
			String token = (String) session.getAttribute("token");
			accountFeign.deposit(token, input);
			return new ModelAndView("redirect:/dashboard?msg=Amount Deposited to the Account");
		} catch (Exception ex) {

			return new ModelAndView("redirect:/dashboard?msg=Provide correct Details");
		}

	}

	/**
	 * @param msg
	 * @param custmsg
	 * @param accmsg
	 * @param deletemsg
	 * @param viewmsg
	 * @param servicemsg
	 * @param model
	 * @return ModelAndView
	 */
	@GetMapping("/dashboard")
	public ModelAndView showdashboard(@RequestParam(defaultValue = "",name = "msg") String msg,
			@RequestParam(defaultValue = "",name = "custmsg") String custmsg ,
			@RequestParam(defaultValue = "",name = "accmsg") String accmsg ,
			@RequestParam(defaultValue = "",name = "deletemsg") String deletemsg ,
			@RequestParam(defaultValue = "",name = "viewmsg") String viewmsg ,
			@RequestParam(defaultValue = "",name = "servicemsg") String servicemsg ,Model model) {
		
		if(session==null)
		{
			return new ModelAndView("redirect:/");
		}
		
		model.addAttribute("custmsg", custmsg);
		model.addAttribute("viewmsg", viewmsg);
		model.addAttribute("accmsg", accmsg);
		model.addAttribute("deletemsg", deletemsg);
		model.addAttribute("servicemsg", servicemsg);
		model.addAttribute("msg", msg);
		return new ModelAndView("dashboard");
	}

	/**
	 * @param request
	 * @return ModelAndView
	 */
	@PostMapping("/withdraw")
	public ModelAndView withdraw(HttpServletRequest request) {
		long accountId = Long.parseLong(request.getParameter("accountId"));
		double amount = Double.parseDouble(request.getParameter("amount"));
		AccountInput input = new AccountInput(accountId, amount);
		log.info(accountId+"--->"+amount);
		try {
			String token = (String) session.getAttribute("token");
			accountFeign.withdraw(token, input);
			return new ModelAndView("redirect:/customerdashboard?msg=Amount Successfully Withdrawn from the Account");
		} catch (Exception ex) {

			return new ModelAndView("redirect:/customerdashboard?msg=Provide correct Details or Minimum Balance is to be Maintained");
		}
	}

	/**
	 * @param request
	 * @return ModelAndView
	 */
	@PostMapping("/transfer")
	public ModelAndView transaction(HttpServletRequest request) {
		long sourceAccountId = Long.parseLong(request.getParameter("accountId"));
		long targetAccountId = Long.parseLong(request.getParameter("targetAccount"));
		String reference = (request.getParameter("reference"));

		double amount = Double.parseDouble(request.getParameter("amount"));
		AccountInput sourceInput = new AccountInput(sourceAccountId, amount);
		AccountInput targetInput = new AccountInput(targetAccountId, amount);
		TransactionInput transaction = new TransactionInput();
		transaction.setSourceAccount(sourceInput);
		transaction.setTargetAccount(targetInput);
		transaction.setReference(reference);
		transaction.setAmount(amount);
		System.err.println(sourceInput);
		System.err.println(targetInput);

		try {
			String token = (String) session.getAttribute("token");
			String check = (String) accountFeign.transaction(token, transaction).getBody();
			System.err.println(check);
			return new ModelAndView("redirect:/customerdashboard?transfermsg="+check);
		} catch (Exception ex) {
			log.error("Error");
			return new ModelAndView("redirect:/customerdashboard?msg=");
		}
	}

	/**
	 * @param msg
	 * @param transfermsg
	 * @param model
	 * @return ModelAndView
	 */ 
	@GetMapping("/customerdashboard")
	public ModelAndView showcustomerdashboard(@RequestParam(defaultValue = "",name = "msg") String msg,
			@RequestParam(defaultValue = "",name = "transfermsg") String transfermsg , Model model) {
		if(session==null)
		{
			return new ModelAndView("redirect:/403"); 
		}
		model.addAttribute("msg", msg);
		model.addAttribute("transfermsg", transfermsg);
		String token = (String) session.getAttribute("token");
		String userId = (String) session.getAttribute("userId");
		CustomerEntity customer = customerFeign.getCustomerDetails(token, userId);
		System.out.println(customer);
		model.addAttribute("customer", customer);
		model.addAttribute("accountinput", new AccountInput());
		return new ModelAndView("customerdashboard");
	}

	/**
	 * @param request
	 * @param model
	 * @return ModelAndView
	 */
	@PostMapping("/customerdashboard/getTransactionsInCustomerdashboard")
	public ModelAndView getTransactionsInCustomerdashboard(HttpServletRequest request, Model model) {
		
		if(session==null)
		{
			return new ModelAndView("redirect:/403");
		} 
		
		String token = (String) session.getAttribute("token");
		String userId = (String) session.getAttribute("userId");
		CustomerEntity customer = customerFeign.getCustomerDetails(token, userId);
		System.out.println(customer);
		model.addAttribute("customer", customer);
		model.addAttribute("accountinput", new AccountInput());
		long accountId = Long.parseLong(request.getParameter("accountId"));
		List<Transaction> transactions = transactionFeign.getTransactionsByAccId(token, accountId);
		model.addAttribute("transactions", transactions);
		return new ModelAndView("customerdashboard");
	}

	/**
	 * @param request
	 * @param model
	 * @return ModelAndView
	 */
	@PostMapping("/deleteCustomer")
	public ModelAndView deleteCustomer(HttpServletRequest request,Model model)
	{
		try {
			String token = (String) session.getAttribute("token");
			String customerId = (String) request.getParameter("customerId");
			log.info("Id--->"+customerId);
			customerFeign.deleteCustomer(token, customerId);
			return new ModelAndView("redirect:/dashboard?deletemsg=Customer Deleted Successfully");
		}
		catch(Exception e)
		{
			return new ModelAndView("redirect:/dashboard?deletemsg=Not Deleted..Wrong Customer ID");
		}
	}
	
	/**
	 * @return ModelAndView
	 */
	@PostMapping("/deductServiceTax")
	public ModelAndView serviceCharges()
	{
		try {
			String token = (String) session.getAttribute("token");
			rulesFeign.serviceCharges(token);
			return new ModelAndView("redirect:/dashboard?servicemsg=Service charges are deducted for Min balance Accounts");
		}
		catch(Exception e)
		{
			return new ModelAndView("redirect:/dashboard?servicemsg=Not deducted...Error!!");
		}
		
	}
	
}
