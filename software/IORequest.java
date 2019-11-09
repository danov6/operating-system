package s340.software;

import s340.hardware.DeviceControllerOperations;

public class IORequest {

	int operation;
	int processNum;
	int count;
	int total;

	public IORequest(int operation, int processNum, int count, int total) {
		super();
		this.operation = operation;
		this.processNum = processNum;
		this.count = count;
		this.total = total;

	}
	
	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}

	public int getTotal() {
		return total;
	}

	public void setTotal(int total) {
		this.total = total;
	}



	public int getProcessNum() {
		return processNum;
	}

	public void setProcessNum(int processNum) {
		this.processNum = processNum;
	}

	public int getOpNum() {
		return operation;
	}

	public void setOpNum(int opNum) {
		this.operation = opNum;
	}

	@Override
	public String toString() {
		return "IOR[OP=" + (operation == DeviceControllerOperations.READ ? "R" : "W") +"("+count+"/"+total+"), PNum= " + processNum + "]";
	}

}
