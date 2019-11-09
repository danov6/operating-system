package s340.software;

public class FreeSpace {

	private int start;
	private int length;

	public FreeSpace(int start, int length) {
		super();
		this.start = start;
		this.length = length;
	}

	public void setStart(int start) {
		this.start = start;
	}

	public void setLength(int length) {
		this.length = length;
	}

	public int getStart() {
		return start;
	}

	public int getLength() {
		return length;
	}

	@Override
	public String toString() {
		return "FreeSpace [start=" + start + ", length=" + length + "]";
	}
	
	

}
