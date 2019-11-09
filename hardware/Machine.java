package s340.hardware;

import s340.hardware.device.Console;
import s340.hardware.device.Disk;
import s340.software.OperatingSystem;

/*
 * A machine - the CPUs, devices, registers, etc.
 */

public class Machine
{

	// memory size

	public final int MEMORY_SIZE = 20000;

	// device numbers

	public final static int NUM_DEVICES = 3;
	
	//devices
	
	public final static int CONSOLE = 0;
	public final static int DISK = 1;
	public final static int DISK2 = 2;


	

	// the various pieces of hardware in a machine

	public final InterruptRegisters interruptRegisters;
	public final IMemoryController memory;
	public final DeviceControlRegister[] controlRegisters;
	public final CPU cpu;	
	public final Device[] devices;
	

	/*
	 * Create the machine and it's hardware.
	 */

	public Machine() throws Exception
	{
		// create memory controller

		memory = new MemoryController(MEMORY_SIZE);

		// create the interrupt registers -- one per device

		interruptRegisters = new InterruptRegisters(NUM_DEVICES);
		for (int i = 0; i < NUM_DEVICES; i++)
		{
			interruptRegisters.register[i] = false;
		}

		// create the device controller registers -- one per device

		controlRegisters = new DeviceControlRegister[NUM_DEVICES];
		for (int i = 0; i < NUM_DEVICES; i++)
		{
			controlRegisters[i] = new DeviceControlRegister(100);
		}

		// create the devices themselves

		devices = new Device[NUM_DEVICES];

		devices[CONSOLE] = new Console(CONSOLE, interruptRegisters, controlRegisters[CONSOLE]);
		devices[DISK] = new Disk(DISK, interruptRegisters, controlRegisters[DISK], new int [Disk.PLATTER_SIZE]);
		devices[DISK2] = new Disk(DISK2, interruptRegisters, controlRegisters[DISK2], new int [10]);

		
		
		

		// create the CPU

		cpu = new CPU(interruptRegisters, memory);
	}

	/*
	 * Power-up the machine.
	 */

	public void powerUp(OperatingSystem os)
	{
		// initialize the CPU interrupt, system call and trap handlers

		cpu.initialize(os, os, os);

		// start the CPU

		new Thread(cpu).start();

		// start the devices

		for (Device device : devices)
		{
			new Thread(device).start();
			
		}
	}
}
