package s340.hardware;

import java.util.Scanner;

import s340.hardware.exception.DecodingFault;
import s340.hardware.exception.PCException;
import s340.hardware.exception.OpcodeException;
import s340.hardware.exception.MemoryFault;

/*
 * The S340 CPU.
 */

public class CPU implements Runnable
{
	// instructions per interrupt

	public final static int INSTRUCTIONS_PER_INTERRUPT = 4;

	// the hardware that the CPU communicates with -- the memory controller and the interrupt registers

	private final IMemoryController memory;
	private final InterruptRegisters interruptRegisters;

	// the software that the CPU communicates with -- the interrupt, system call and trap handlers.

	private IInterruptHandler interruptHandler;
	private ISystemCallHandler sysCallHandler;
	private ITrapHandler trapHandler;

	// private CPU internal state -- the program counter, instruction count, current opcode and operand

	private int pc;
	private int opcode;
	private int operand;

	// public CPU internal state -- the accumulator and index registers, the mode, and the instruction count

	public int acc;
	public int x;
	public Mode mode;
	public int instructionCount;

	// do not mess with this

	public boolean runProg = false;

	// for input from the keyboard

	private final Scanner input;

	/*
	 * Create the CPU "connected to" the relevant hardware
	 */

	public CPU(InterruptRegisters interruptRegisters, IMemoryController memory)
	{
		this.interruptRegisters = interruptRegisters;
		this.memory = memory;
		input = new Scanner(System.in);
	}

	/*
	 * Initialize the CPU with the various handlers.
	 *
	 * @param interruptHandler
	 * @param systemCallHandler
	 * @param trapHandler
	 */

	public void initialize(IInterruptHandler interruptHandler, ISystemCallHandler sysCallHandler,
			ITrapHandler trapHandler)
	{
		this.interruptHandler = interruptHandler;
		this.sysCallHandler = sysCallHandler;
		this.trapHandler = trapHandler;
		instructionCount = 0;
	}

	/*
	 * Set the program counter.
	 */

	public void setPc(int pc)
	{
		this.pc = pc;
	}

	/*
	 * Poll the hardware for interrupts.
	 */

	private void pollForInterrupts()
	{
		for (int i = 0; i < interruptRegisters.register.length; i++)
		{
			if (interruptRegisters.register[i])
			{
				interrupt(i);
			}
		}
	}

	/*
	 * Fetch the next opcode and operand and update the program counter.
	 */

	private void fetch() throws PCException, MemoryFault
	{
		if (pc < 0)
		{
			throw new PCException(pc);
		}

		opcode = memory.load(pc++);
		operand = memory.load(pc++);
	}

	/*
	 * Switch to system mode and perform a trap.
	 */

	private void trap(int trapNumber)
	{
		mode = Mode.SYSTEM;
		trapHandler.trap(pc, trapNumber);
	}

	/*
	 * Switch to system mode and perform an interrupt.
	 */

	private void interrupt(int trapNumber)
	{
		mode = Mode.SYSTEM;
		interruptHandler.interrupt(pc, trapNumber);
	}

	/*
	 * Decode and execute an instruction.
	 *
	 * @param instruction the instruction.
	 */

	private void decode() throws OpcodeException, MemoryFault
	{
		switch (opcode)
		{
			case Opcode.LOAD:
				acc = memory.load(operand);
				break;
			case Opcode.LOADI:
				acc = operand;
				break;
			case Opcode.LOADX:
				acc = memory.load(operand + x);
				break;
			case Opcode.STORE:
				memory.store(operand, acc);
				break;
			case Opcode.STOREX:
				memory.store(operand + x, acc);
				break;
			case Opcode.TAX:
				x = acc;
				break;
			case Opcode.TXA:
				acc = x;
				break;
			case Opcode.INCA:
				acc++;
				break;
			case Opcode.INCX:
				x++;
				break;
			case Opcode.ADD:
				acc += memory.load(operand);
				break;
			case Opcode.ADDI:
				acc += operand;
				break;
			case Opcode.SUB:
				acc -= memory.load(operand);
				break;
			case Opcode.SUBI:
				acc -= operand;
				break;
			case Opcode.MUL:
				acc *= memory.load(operand);
				break;
			case Opcode.DIV:
				int divisor = memory.load(operand);
				if (divisor == 0)
				{
					mode = Mode.SYSTEM;
					trap(Trap.DIV_ZERO);
				}
				else
				{
					acc /= divisor;
				}
				break;
			case Opcode.JMP:
				pc = operand;
				break;
			case Opcode.JPOS:
				if (acc > 0)
				{
					pc = operand;
				}
				break;
			case Opcode.JZERO:
				if (acc == 0)
				{
					pc = operand;
				}
				break;
			case Opcode.JNEG:
				if (acc < 0)
				{
					pc = operand;
				}
				break;
			case Opcode.SYSCALL:
				mode = Mode.SYSTEM;
				sysCallHandler.syscall(pc, operand);
				break;
			case Opcode.END:
				trap(Trap.END);
				break;
			case Opcode.INPUT:
				System.out.println("Input value : ");
				acc = input.nextInt();
				break;
			case Opcode.OUTPUT:
				System.out.println("Output = " + acc);
				break;
			default:
				throw new OpcodeException(opcode);
		}
	}

	@Override
	public void run()
	{
		try
		{
			instructionCount = 0;
			while (true)
			{
				if (!runProg)
				{
					Thread.sleep(100);
					continue;
				}

				pollForInterrupts();
				
				fetch();
				decode();
				//System.out.println(Opcode.toString(pc - 2, opcode, operand) + "   ->   " + pc + ", " + acc + ", " + x);

				instructionCount++;
				if (instructionCount == INSTRUCTIONS_PER_INTERRUPT)
				{
					instructionCount = 0;
					trap(Trap.TIMER);
				}
			}
		}
		catch (DecodingFault ex)
		{
			ex.printStackTrace(System.err);
			trap(Trap.DECODE_FAULT);
		}
		catch (MemoryFault ex)
		{
			trap(Trap.MEMORY_FAULT);
		}
		catch (Throwable ex)
		{
			ex.printStackTrace(System.err);
			trap(Trap.CPU_FAULT);
		}
	}
}
