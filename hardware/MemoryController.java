package s340.hardware;

import s340.hardware.exception.MemoryAddressException;
import s340.hardware.exception.MemoryFault;

/*
 * A basic memory controller.
 */

public class MemoryController implements IMemoryController
{

	private final int[] memory;
	
	public int base = 0;
	public int limit = 0;

	public MemoryController(int[] contents)
	{
		this.memory = contents;
	}

	public MemoryController(int size)
	{
		this(new int[size]);
	}

	/*
	 * Check if a memory address is valid.
	 */

	private void checkAddress(int address) throws MemoryAddressException
	{
		if (address < 0 || address >= limit)
		{
			throw new MemoryAddressException(address);
		}
	}

	/*
	 * Load the contents of a given memory address.
	 */

	public int getBase() {
		return base;
	}

	public int getLimit() {
		return limit;
	}

	public void setBase(int base) {
		this.base = base;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}

	@Override
	public int load(int address) throws MemoryFault
	{
		checkAddress(address);
		return memory[address + base];
	}

	/*
	 * Store a value into a given memory address.
	 */

	@Override
	public void store(int address, int value) throws MemoryFault
	{
		checkAddress(address);
		memory[address + base] = value;
	}

}