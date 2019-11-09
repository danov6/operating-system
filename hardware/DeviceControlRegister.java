package s340.hardware;

import java.util.concurrent.Semaphore;

/*
 * An I/O device control register.
 */

public class DeviceControlRegister
{

	public int[] register;
	public final Semaphore startOperation;

	public DeviceControlRegister(int n)
	{
		register = new int[n];
		startOperation = new Semaphore(0);
	}

	public void startOperation()
	{
		startOperation.release();
	}
}
