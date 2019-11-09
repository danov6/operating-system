package s340.hardware;

/*
 * The interrupt registers for the S340 CPU.
 */

public class InterruptRegisters
{
	public boolean register[];

	public InterruptRegisters(int n)
	{
		register = new boolean[n];
	}
}
