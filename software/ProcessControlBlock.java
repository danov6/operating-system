package s340.software;

public class ProcessControlBlock {
	ProcessState status;
	public int x;
	public int acc;
	public int pc;
	public int base;
	public int limit;
	
	//all pcbs start as "NEW"
	
	public int getBase() {
		return base;
	}

	public void setBase(int base) {
		this.base = base;
	}

	public int getLimit() {
		return limit;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}

	public ProcessControlBlock(){
		this.status = ProcessState.NEW;
	}
	
	//getters and setters for registers and status
	
	public ProcessState getStatus() {
		return status;
	}
	public void setStatus(ProcessState status) {
		this.status = status;
	}
	public int getX() {
		return x;
	}
	public void setX(int x) {
		this.x = x;
	}
	public int getAcc() {
		return acc;
	}
	public void setAcc(int acc) {
		this.acc = acc;
	}
	public int getPc() {
		return pc;
	}
	public void setPc(int pc) {
		this.pc = pc;
	}
	
}
