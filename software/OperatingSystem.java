package s340.software;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import s340.hardware.DeviceControllerOperations;
import s340.hardware.IInterruptHandler;
import s340.hardware.ISystemCallHandler;
import s340.hardware.ITrapHandler;
import s340.hardware.Machine;
import s340.hardware.MemoryController;
import s340.hardware.Trap;
import s340.hardware.device.Disk;
import s340.hardware.exception.MemoryFault;

/*
 * The operating system that controls the software running on the S340 CPU.
 *
 * The operating system acts as an interrupt handler, a system call handler, and
 * a trap handler.
 */

public class OperatingSystem implements IInterruptHandler, ISystemCallHandler, ITrapHandler {
	// position has to start -1 because process table is 0-10
	public ProcessControlBlock[] process_table;
	public static final int MAX_NUM_PROCESSES = 10;
	public int process_table_position = -1;
	public int[] headPosition = new int[Machine.NUM_DEVICES];

	// the machine on which we are running.

	private final Machine machine;

	// creating the free space list
	private LinkedList<FreeSpace> freeSpaceList = new LinkedList<>();

	// creates queues depending on the number of devices

	public LinkedList<IORequest>[] Q = new LinkedList[Machine.NUM_DEVICES];

	/*
	 * Create an operating system on the given machine. giving the process table
	 * a size and filled with pcbs
	 */

	public OperatingSystem(Machine machine) throws MemoryFault {
		this.machine = machine;
		process_table = new ProcessControlBlock[MAX_NUM_PROCESSES];
		for (int i = 0; i < MAX_NUM_PROCESSES; i++) {
			process_table[i] = new ProcessControlBlock();
		}
		freeSpaceList.add(new FreeSpace(0, machine.MEMORY_SIZE));
		ProgramBuilder wait = new ProgramBuilder();
		wait.start(0);
		wait.jmp(0);
		loadProgram(0, wait.build());

		for (int i = 0; i < Machine.NUM_DEVICES; i++) {
			Q[i] = new LinkedList<IORequest>();
		}

		//Sets the arm location for the devices at platter start 0
		for (int i = 0; i < Machine.NUM_DEVICES; i++) {
			headPosition[i] = 0;
		}
	}

	/*
	 * runs the next process that is ready, sets to running and loops past 0
	 */

	public void runNextProcess() {
		for (int i = 0; i < MAX_NUM_PROCESSES; i++) {
			process_table_position = (process_table_position + 1) % MAX_NUM_PROCESSES;
			if (process_table[process_table_position].getStatus() == ProcessState.READY) {
				process_table[process_table_position].setStatus(ProcessState.RUNNING);
				machine.cpu.acc = process_table[process_table_position].getAcc();
				machine.cpu.x = process_table[process_table_position].getX();
				machine.cpu.setPc(process_table[process_table_position].getPc());
				((MemoryController) machine.memory).setBase(process_table[process_table_position].getBase());
				((MemoryController) machine.memory).setLimit(process_table[process_table_position].getLimit());
				// System.out.println("now running: " + process_table_position);

				return;
			}
		}
		// sets physical memory for wait process
		process_table_position = -1;
		// System.out.println("now running: " + process_table_position);
		((MemoryController) machine.memory).setBase(0);
		((MemoryController) machine.memory).setLimit(4);
		machine.cpu.setPc(0);

	}

	/*
	 * // iterates through the freespace linked list and returns the start
	 * address // of the free space that is large enough for the size parameter
	 * // it will also remove or reduce the size of the free space // otherwise
	 * it will return -1 which is not in memory
	 */
	public int findFreeSpace(int size) {
		int x = 0;
		for (FreeSpace space : freeSpaceList) {
			if (space.getLength() > size) {
				x = space.getStart();
				space.setStart(space.getStart() + size);
				space.setLength(space.getLength() - size);

				return x;
			} else if (space.getLength() == size) {
				freeSpaceList.remove(space);
				return space.getStart();
			}
		}
		return -1;
	}

	/*
	 * Load a program into a given memory address starting at the identified
	 * start place
	 */
	private int loadProgram(int startAddress, Program program) throws MemoryFault {
		((MemoryController) machine.memory).setBase(0);
		((MemoryController) machine.memory).setLimit(machine.MEMORY_SIZE);
		int address = findFreeSpace(program.getCode().length + program.getDataSize());
		int processStart = address;
		for (int i : program.getCode()) {
			machine.memory.store(address++, i);
		}

		return processStart;
	}

	/*
	 * Scheduled a list of programs to be run.
	 * 
	 * loop makes a new/terminated pcb able to be used again and is set to ready
	 * 
	 * @param programs the programs to schedule
	 */

	public void schedule(Program... programs) throws MemoryFault {
		int address = 0;
		for (Program program : programs) {
			address = loadProgram(address, program);
			for (int k = 0; k < MAX_NUM_PROCESSES; k++)
				if (process_table[k].getStatus() == ProcessState.TERMINATED
						|| process_table[k].getStatus() == ProcessState.NEW) {
					process_table[k].setStatus(ProcessState.READY);
					process_table[k].setPc(0);
					process_table[k].setBase(address);
					process_table[k].setLimit(program.getCode().length + program.getDataSize());
					// System.out.println("At position: " + k + " base is: "+
					// process_table[k].getBase() +" limit is: "+
					// process_table[k].getLimit());
					break;
				}

		}

		// leave this as the last line
		machine.cpu.runProg = true;

	}

	/*
	 * Handle a trap from the hardware. rather than ending op sys, used
	 * "runNextProcess()"
	 * 
	 * @param programCounter -- the program counter of the instruction after the
	 * one that caused the trap.
	 * 
	 * @param trapNumber -- the trap number for this trap.
	 */

	@Override
	public void trap(int savedProgramCounter, int trapNumber) {
		CheckValid.trapNumber(trapNumber);
		if (!machine.cpu.runProg) {
			return;
		}
		// save registers, set status to ready/terminated
		if (process_table_position != -1) {
			saveRegisters(savedProgramCounter);

			process_table[process_table_position].setStatus(ProcessState.READY);
		}

		// System.out.println("Timer " + savedProgramCounter);

		switch (trapNumber) {
		case Trap.TIMER:
			runNextProcess();
			break;
		case Trap.END:
			process_table[process_table_position].setStatus(ProcessState.TERMINATED);
			runNextProcess();
			break;
		default:
			System.out.println("UNHANDLED TRAP " + trapNumber);
			System.exit(1);
		}
	}

	/*
	 * Handle a system call from the software.
	 * 
	 * @param programCounter -- the program counter of the instruction after the
	 * one that caused the trap.
	 * 
	 * @param callNumber -- the callNumber of the system call.
	 * 
	 * @param address -- the memory address of any parameters for the system
	 * call.
	 */

	@Override
	public void syscall(int savedProgramCounter, int callNumber) {
		CheckValid.syscallNumber(callNumber);
		if (!machine.cpu.runProg) {
			return;
		}
		saveRegisters(savedProgramCounter);
		switch (callNumber) {
		// system call for Sbrk getting the accumulator then running next
		case SystemCall.SBRK:
			sbrk(machine.cpu.acc);
			break;
		// system call for writing a passing parameter to the screen
		case SystemCall.WRITE_CONSOLE:
			writeConsole(process_table[process_table_position].getAcc());
			break;
		case SystemCall.READ: {
			((MemoryController) machine.memory).setBase(process_table[process_table_position].getBase());
			((MemoryController) machine.memory).setLimit(process_table[process_table_position].getLimit());
			int Acc = process_table[process_table_position].getAcc();
			try {
				// System.out.println("Read: ");

				read(machine.memory.load(Acc), machine.memory.load(Acc + 1), machine.memory.load(Acc + 2),
						machine.memory.load(Acc + 3));
			} catch (MemoryFault e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
			break;

		case SystemCall.WRITE:
			((MemoryController) machine.memory).setBase(process_table[process_table_position].getBase());
			((MemoryController) machine.memory).setLimit(process_table[process_table_position].getLimit());
			int Acc = process_table[process_table_position].getAcc();
			try {

				write(machine.memory.load(Acc), machine.memory.load(Acc + 1), machine.memory.load(Acc + 2),
						machine.memory.load(Acc + 3), machine.memory.load(Acc + 4));

			} catch (MemoryFault e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;
		}
		showIOR("SysCall");
		runNextProcess();
	}

	// passes a parameter that you would like to print to the console

	// Disk instead of passing the acc, you are passing the address of the first

	private void writeConsole(int Write) {

		process_table[process_table_position].setStatus(ProcessState.WAITING);

		if (Q[Machine.CONSOLE].isEmpty()) {
			Q[Machine.CONSOLE].add(new IORequest(DeviceControllerOperations.WRITE, process_table_position, 1, 1));
			// starts the console write
			machine.devices[Machine.CONSOLE].controlRegister.register[1] = Write;
			machine.devices[Machine.CONSOLE].controlRegister.startOperation();
		} else {
			Q[Machine.CONSOLE].add(new IORequest(DeviceControllerOperations.WRITE, process_table_position, 1, 1));

		}

	}

	public void read(int deviceNum, int platterNum, int platterStart, int length) {

		int total = length / ((Disk) machine.devices[deviceNum]).buffer.length;
		if (length % ((Disk) machine.devices[deviceNum]).buffer.length != 0) {
			total++;
		}
		process_table[process_table_position].setStatus(ProcessState.WAITING);

		if (Q[deviceNum].isEmpty()) {
			Q[deviceNum].add(new IORequest(DeviceControllerOperations.READ, process_table_position, 1, total));
			performRead(deviceNum, platterNum, platterStart, length);

		} else {
			Q[deviceNum].add(new IORequest(DeviceControllerOperations.READ, process_table_position, 1, total));
		}

	}

	public void performRead(int deviceNum, int platterNum, int platterStart, int length) {
		int bufferLength = ((Disk) machine.devices[deviceNum]).buffer.length;

		System.out.println("PlatterStart: " + platterStart);

		
		machine.devices[deviceNum].controlRegister.register[0] = DeviceControllerOperations.READ;
		machine.devices[deviceNum].controlRegister.register[1] = platterNum;
		machine.devices[deviceNum].controlRegister.register[2] = platterStart
				+ ((Q[deviceNum].element().getCount() - 1) * bufferLength);
		if (length > bufferLength) {
			machine.devices[deviceNum].controlRegister.register[3] = bufferLength;
		} else {
			machine.devices[deviceNum].controlRegister.register[3] = length;
		}
		machine.devices[deviceNum].controlRegister.startOperation();

	}

	public void write(int deviceNum, int platterNum, int platterStart, int length, int mem) {

		int bufferLength = ((Disk) machine.devices[deviceNum]).buffer.length;
		int total = length / bufferLength;

		if (length % bufferLength != 0) {
			total++;
		}
		process_table[process_table_position].setStatus(ProcessState.WAITING);

		if (Q[deviceNum].isEmpty()) {
			Q[deviceNum].add(new IORequest(DeviceControllerOperations.WRITE, process_table_position, 1, total));
			performWrite(deviceNum, platterNum, platterStart, length, mem);

		} else {
			Q[deviceNum].add(new IORequest(DeviceControllerOperations.WRITE, process_table_position, 1, total));
		}
	}

	public void performWrite(int deviceNum, int platterNum, int platterStart, int length, int mem) {

		int bufferLength = ((Disk) machine.devices[deviceNum]).buffer.length;

		mem = mem + ((Q[deviceNum].element().getCount() - 1) * bufferLength);

		if (length > bufferLength) {
			for (int i = 0; i < bufferLength; i++) {
				try {
					((Disk) machine.devices[deviceNum]).buffer[i] = machine.memory.load(mem + i);
					// System.out.println("MEM " + (mem + i) + ": " + ((Disk)
					// machine.devices[deviceNum]).buffer[i]);
				} catch (MemoryFault e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} else {
			for (int i = 0; i < remainingLength(length, Q[deviceNum].element().getTotal(),
					Q[deviceNum].element().getTotal(), deviceNum); i++) {
				try {
					((Disk) machine.devices[deviceNum]).buffer[i] = machine.memory.load(mem + i);
					// System.out.println(
					// "MEM " + (mem + i) + ": " + ((Disk)
					// machine.devices[deviceNum]).buffer[i]);
				} catch (MemoryFault e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		System.out.println("PlatterStart: " + platterStart);

		machine.devices[deviceNum].controlRegister.register[0] = DeviceControllerOperations.WRITE;
		machine.devices[deviceNum].controlRegister.register[1] = platterNum;
		machine.devices[deviceNum].controlRegister.register[2] = platterStart
				+ ((Q[deviceNum].element().getCount() - 1) * bufferLength);
		if (length > bufferLength) {
			machine.devices[deviceNum].controlRegister.register[3] = bufferLength;
		} else {
			machine.devices[deviceNum].controlRegister.register[3] = length;

		}
		machine.devices[deviceNum].controlRegister.startOperation();

	}

	// write console,
	// method that allows for a process to expand in place if there is a free
	// space
	// directly following a program. uses an iterator for reliable delete method
	// of a list element
	// returns true if expanded in place returns false if didn't
	private boolean expandInPlace(int newSize) {
		int freeSpaceStart = process_table[process_table_position].getBase()
				+ process_table[process_table_position].getLimit();
		Iterator<FreeSpace> spaceIt = freeSpaceList.iterator();
		while (spaceIt.hasNext()) {
			FreeSpace f1 = spaceIt.next();
			if (f1.getStart() == freeSpaceStart && f1.getLength() >= newSize) {
				process_table[process_table_position]
						.setLimit(process_table[process_table_position].getLimit() + newSize);

				if (f1.getLength() == 0) {
					spaceIt.remove();
				} else {
					f1.setStart(f1.getStart() + newSize);
					f1.setLength(f1.getLength() - newSize);
				}

				return true;
			}
		}
		return false;
	}

	// moves the actual program from the old location in physical memory to new
	// location
	private void physicalMove(int newSpot, int oldbase) {
		for (int i = 0; i < process_table[process_table_position].getLimit(); i++) {
			try {
				((MemoryController) machine.memory).setBase(oldbase);
				int x = machine.memory.load(i);
				((MemoryController) machine.memory).setBase(newSpot);
				machine.memory.store(i, x);
			} catch (MemoryFault e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	// moves the physical address to a location based on a given process control
	// block that is in the pcb list

	private void compactionPhysicalMove(int newSpot, int oldbase, ProcessControlBlock pcb) {
		for (int i = 0; i < pcb.getLimit(); i++) {
			try {
				((MemoryController) machine.memory).setBase(oldbase);
				int x = machine.memory.load(i);
				((MemoryController) machine.memory).setBase(newSpot);
				machine.memory.store(i, x);
			} catch (MemoryFault e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		pcb.setBase(newSpot);
	}

	/*
	 * // moves the pointer of the process to the old free space that was
	 * 
	 * // large enough to fit new allocations, moves both the base and limit //
	 * saves previous base and limit and adds a free space where the old program
	 * // came from // either resizes the free space or removes it from the list
	 * depending on if // the // free space size is equal to the program size //
	 * returns true if process moves, returns false if doesn't move
	 */

	private boolean moveProcess(int newSize) {
		int totalSize = process_table[process_table_position].getLimit() + newSize;
		int oldBase = process_table[process_table_position].getBase();
		for (FreeSpace space : freeSpaceList) {
			if (space.getLength() > totalSize) {
				int oldProcessStart = process_table[process_table_position].getBase();
				int oldProcessLimit = process_table[process_table_position].getLimit();
				process_table[process_table_position].setBase(space.getStart());
				process_table[process_table_position].setLimit(totalSize);
				physicalMove(process_table[process_table_position].getBase(), oldBase);
				space.setStart(space.getStart() + totalSize);
				space.setLength(space.getLength() - totalSize);

				freeSpaceList.add(new FreeSpace(oldProcessStart, oldProcessLimit));

				return true;

			} else if (space.getLength() == totalSize) {
				int oldProcessStart = process_table[process_table_position].getBase();
				int oldProcessLimit = process_table[process_table_position].getLimit();
				freeSpaceList.remove(space);
				process_table[process_table_position].setBase(space.getStart());
				process_table[process_table_position].setLimit(totalSize);
				physicalMove(process_table[process_table_position].getBase(), oldBase);

				freeSpaceList.add(new FreeSpace(oldProcessStart, oldProcessLimit));
				return true;
			}
		}
		return false;
	}

	/*
	 * // creates LL of pcbs and uses pcbs from process table // sorts pcbs by
	 * base // physically and virtually moves the first pcb to the start of
	 * memory the // subsequent sorted processes to the next start position
	 * after that pcb // removes all elements in freespace list and adds a new
	 * freespace from the // end of the last process to the end of memory //
	 * after it compacts all processes to the start of memory it takes all the
	 * // processes futher along in memory from the current process that is //
	 * expanding and moves them to the furthest spot in memory so that process
	 * // can expand in place
	 */
	private void compactProcesses() {
		LinkedList<ProcessControlBlock> pcbList = new LinkedList<>();
		for (int i = 0; i < process_table.length; i++) {
			if (process_table[i] != null)
				pcbList.add(process_table[i]);
		}

		Collections.sort(pcbList, new Comparator<ProcessControlBlock>() {
			public int compare(ProcessControlBlock pcb1, ProcessControlBlock pcb2) {
				return pcb1.getBase() - pcb2.getBase();
			}
		});

		int position = 4;
		for (ProcessControlBlock b : pcbList) {

			compactionPhysicalMove(position, b.getBase(), b);
			b.setBase(position);
			position += b.getLimit();

		}
		freeSpaceList.removeAll(freeSpaceList);
		int oldSize = machine.MEMORY_SIZE - position;
		freeSpaceList.add(new FreeSpace(position, oldSize));

		Iterator<ProcessControlBlock> pcbIt = pcbList.descendingIterator();
		while (pcbIt.hasNext()) {
			ProcessControlBlock f1 = pcbIt.next();
			if (f1.getLimit() + f1.getBase() == freeSpaceList.getFirst().getStart()
					&& f1.getBase() != process_table[process_table_position].getBase()) {
				compactionPhysicalMove(
						freeSpaceList.getFirst().getStart() + freeSpaceList.getFirst().getLength() - f1.getLimit(),
						f1.getBase(), f1);
				f1.setBase(freeSpaceList.getFirst().getStart() + freeSpaceList.getFirst().getLength() - f1.getLimit());
				freeSpaceList.removeAll(freeSpaceList);
				freeSpaceList.add(new FreeSpace(f1.getBase() - oldSize, oldSize));

			} else {
				break;
			}
		}

	}

	private void sbrk(int newSize) {
		if (expandInPlace(newSize)) {
			System.out.println("Expanded in place");
			return;
		}
		System.out.println("Could not expand");

		Collections.sort(freeSpaceList, new Comparator<FreeSpace>() {
			public int compare(FreeSpace f1, FreeSpace f2) {
				return f1.getStart() - f2.getStart();
			}
		});
		Iterator<FreeSpace> freeSpaceIt = freeSpaceList.iterator();
		FreeSpace prev = null;
		while (freeSpaceIt.hasNext()) {
			if (prev == null) {
				prev = freeSpaceIt.next();
			} else {
				FreeSpace curr = freeSpaceIt.next();
				if (prev.getStart() + prev.getLength() == curr.getStart()) {
					prev.setLength(prev.getLength() + curr.getLength());
					freeSpaceIt.remove();
				} else {
					prev = curr;
				}
			}
		}
		System.out.println("Free Spaces are merged");
		if (expandInPlace(newSize)) {
			System.out.println("Expanded in place");
			return;
		}
		System.out.println("Could not expand after merge");
		if (moveProcess(newSize)) {
			System.out.println("Process was moved");
			return;
		}
		System.out.println("Could not move process");
		compactProcesses();
		if (expandInPlace(newSize)) {
			System.out.println("Expanded in place");
			return;
		}
		System.out.println("Memory Size is too small");

	}

	private void saveRegisters(int savedProgramCounter) {
		if (process_table_position != -1) {
			process_table[process_table_position].setAcc(machine.cpu.acc);
			process_table[process_table_position].setX(machine.cpu.x);
			process_table[process_table_position].setPc(savedProgramCounter);
		}

	}

	//This routine will calculate the remaining length of an operation.
	//It will accept the original length, the count, the total number of operations,
	//and the device number to determine its buffer size.
	public int remainingLength(int length, int count, int total, int deviceNum) {

		int bufferLength = ((Disk) machine.devices[deviceNum]).buffer.length;
		int trueLength = length;

		if (count == total) {
			if (total > 1) {
				for (int i = 1; i < total; i++) {
					trueLength -= bufferLength;
				}
			} else {
				trueLength = length;
			}
		} else {
			for (int i = 1; i < count; i++) {
				trueLength -= bufferLength;
			}
		}
		return trueLength;
	}

/*	This method will take the head request that was completed and sent to the interrupt handler.
	If the length of an operation is greater than the size of the buffer, the operation will get split up into parts.
	This will basically check if all parts are completed.  If the count is less than the total, it will immediately
	add the next part of the operation to the end of the list.  If the operation was a read, the buffer will
	copy only as much as its length permits.  The variable "mem" is what memory location the data is copying to
	and is increased based on the count and the buffer size.
	
	Else if the total is equal to the count, the process state is set to ready because all parts of the IORequest
	have been completed.  If the operation was a read, it will copy the remaining length from the buffer to memory.
	
	    */
	public void checkIOCompletion(int deviceNumber, IORequest head) {

		if (head.getCount() < head.getTotal()) {
			Q[deviceNumber].add(new IORequest(head.getOpNum(), head.getProcessNum(), head.getCount() + 1, head.getTotal()));
			((MemoryController) machine.memory).setBase(process_table[head.getProcessNum()].getBase());
			((MemoryController) machine.memory).setLimit(process_table[head.getProcessNum()].getLimit());
			int Acc = process_table[head.getProcessNum()].getAcc();
			try {
				if (head.getOpNum() == DeviceControllerOperations.READ) {
					int bufferLength = ((Disk) machine.devices[deviceNumber]).buffer.length;
					int mem = machine.memory.load(Acc + 4) + ((head.getCount() - 1) * bufferLength);

					for (int i = 0; i < bufferLength; i++) {
						try {
							machine.memory.store(mem + i, ((Disk) machine.devices[deviceNumber]).buffer[i]);
							// System.out.println(
							// "MEM " + (mem + i) + ": " + ((Disk)
							// machine.devices[deviceNumber]).buffer[i]);

						} catch (MemoryFault e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

					}

				}
			} catch (MemoryFault e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			process_table[head.getProcessNum()].setStatus(ProcessState.READY);

			if (head.getOpNum() == DeviceControllerOperations.READ) {

				((MemoryController) machine.memory).setBase(process_table[head.getProcessNum()].getBase());
				((MemoryController) machine.memory).setLimit(process_table[head.getProcessNum()].getLimit());

				int Acc = process_table[head.getProcessNum()].getAcc();

				try {
					int bufferLength = ((Disk) machine.devices[deviceNumber]).buffer.length;
					int length = machine.memory.load(Acc + 3);
					int mem = machine.memory.load(Acc + 4) + ((head.getCount() - 1) * bufferLength);

					for (int i = 0; i < remainingLength(length, head.getCount(), head.getTotal(), deviceNumber); i++) {
						try {
							machine.memory.store(mem + i, ((Disk) machine.devices[deviceNumber]).buffer[i]);
							// System.out.println(
							// "MEM " + (mem + i) + ": " + ((Disk)
							// machine.devices[deviceNumber]).buffer[i]);

						} catch (MemoryFault e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

					}
				} catch (MemoryFault e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}
	}

/*	This method will return the next closest IORequest to the arm location.  It will scan through the list of requests
	for a given device and subtract the arm location from each of their platter start locations.
	The one with the shortest distance is returned from its position in the linked list*/
	
	public IORequest closest(int armLocation, LinkedList<IORequest> Q) throws MemoryFault {

		int smallest = Integer.MAX_VALUE;
		int smallestPos = 0;

		for (int i = 0; i < Q.size(); i++) {
			((MemoryController) machine.memory).setBase(process_table[Q.get(i).getProcessNum()].getBase());
			((MemoryController) machine.memory).setLimit(process_table[Q.get(i).getProcessNum()].getLimit());
			int nextLocation = machine.memory.load(process_table[Q.get(i).getProcessNum()].getAcc() + 2);
			if (Math.abs(armLocation - nextLocation) < smallest) {
				smallest = Math.abs(armLocation - nextLocation);
				smallestPos = i;

			}
	//		System.out.print(nextLocation+",");

		}
		System.out.println("Closest = "+ smallest);
		return Q.get(smallestPos);
	}
	/*
	 * Handle an interrupt from the hardware.
	 * 
	 * @param programCounter -- the program counter of the instruction after the
	 * one that caused the trap.
	 * 
	 * @param deviceNumber -- the device number that is interrupting.
	 */

	@Override
	public void interrupt(int savedProgramCounter, int deviceNumber) {

		CheckValid.deviceNumber(deviceNumber);
		if (!machine.cpu.runProg) {
			return;
		}
		// Clears interrupt flag
		// Saves registers
		// Assigns the head of the queue, the one to be removed
		machine.devices[deviceNumber].interruptRegisters.register[deviceNumber] = false;
		saveRegisters(savedProgramCounter);
		IORequest head = Q[deviceNumber].remove();
		checkIOCompletion(deviceNumber, head);

		if (!Q[deviceNumber].isEmpty()) {

			if (deviceNumber == Machine.CONSOLE) {

				IORequest newHead = Q[deviceNumber].element();
				// starts the console write
				machine.devices[Machine.CONSOLE].controlRegister.register[1] = process_table[newHead.getProcessNum()]
						.getAcc();
				machine.devices[Machine.CONSOLE].controlRegister.startOperation();
			} else {
				IORequest newHead = null;

				try {
					//If the queue is not empty and the device is not the console:
					//-This will check the head position after the first operation
					//completes.
					//-Goes through a routine that scans the list and returns the next closest request to the head position
					//-Removes that request and places it in front of the list
					
					int oldAcc = process_table[head.getProcessNum()].getAcc();
					((MemoryController) machine.memory).setBase(process_table[head.getProcessNum()].getBase());
					((MemoryController) machine.memory).setLimit(process_table[head.getProcessNum()].getLimit());
					headPosition[deviceNumber] = machine.memory.load(oldAcc + 2) + machine.memory.load(oldAcc + 3);
					
					System.out.println("Head Position after completing Operation " +head.getProcessNum()+":   " + headPosition[deviceNumber]);
					// sets the head location to the next closest request
					newHead = closest(headPosition[deviceNumber], Q[deviceNumber]);

					Q[deviceNumber].remove(newHead);
					Q[deviceNumber].addFirst(newHead);


				} catch (MemoryFault e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

				((MemoryController) machine.memory).setBase(process_table[newHead.getProcessNum()].getBase());
				((MemoryController) machine.memory).setLimit(process_table[newHead.getProcessNum()].getLimit());
				if (newHead.getOpNum() == DeviceControllerOperations.WRITE) {

					// load parameters from memory
					int Acc = process_table[newHead.getProcessNum()].getAcc();
					try {

						performWrite(machine.memory.load(Acc), machine.memory.load(Acc + 1),
								machine.memory.load(Acc + 2), machine.memory.load(Acc + 3),
								machine.memory.load(Acc + 4));

					} catch (MemoryFault e) {
						// TODO Auto-generated catch block
						e.printStackTrace();

					}
				} else {

					// load parameters from memory
					int Acc = process_table[newHead.getProcessNum()].getAcc();
					try {

						performRead(machine.memory.load(Acc), machine.memory.load(Acc + 1),
								machine.memory.load(Acc + 2), machine.memory.load(Acc + 3));

					} catch (MemoryFault e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}
			}

		}
		showIOR("Interrupt");

		if (process_table_position == -1) {
			((MemoryController) machine.memory).setBase(0);
			((MemoryController) machine.memory).setLimit(4);
			machine.cpu.setPc(0);
		} else {
			machine.cpu.acc = process_table[process_table_position].getAcc();
			machine.cpu.x = process_table[process_table_position].getX();
			machine.cpu.setPc(process_table[process_table_position].getPc());
			((MemoryController) machine.memory).setBase(process_table[process_table_position].getBase());
			((MemoryController) machine.memory).setLimit(process_table[process_table_position].getLimit());
		}

		// System.out.println("Ops in Dev "+deviceNumber+": " +
		// Q[Machine.CONSOLE].size());
	}

	private void showIOR(String Message) {
		System.out.println("IO Queues: " + Message);
		for (int i = 0; i < Machine.NUM_DEVICES; i++) {
			System.out.print(i + ": ");
			for (IORequest R : Q[i]) {
				System.out.print(R);
				System.out.print(", ");

			}
			System.out.println();

		}
	}

}