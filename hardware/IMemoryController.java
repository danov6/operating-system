package s340.hardware;

import s340.hardware.exception.MemoryFault;

/*
 * A memory controller.
 */

public interface IMemoryController
{

	int load(int address) throws MemoryFault;

	void store(int address, int value) throws MemoryFault;

}