package s340;

import s340.hardware.Machine;
import s340.software.Program;
import s340.software.OperatingSystem;
import s340.software.ProgramBuilder;
import s340.software.SystemCall;

/**
 *
 */
public class Main {
	public static void main(String[] args) throws Exception {
		// Setup the hardware, the operating system, and power up.
		// Do not remove this!

		Machine machine = new Machine();
		OperatingSystem os = new OperatingSystem(machine);
		machine.powerUp(os);

		//Memory locations and length
		int dataAdd = 1000;
		int writeAdd = 300;
		int length = 15;
		int readAdd = 600;

		Program[] Programs = new Program[4];

		for (int i = 0; i < 3; i++) {
			ProgramBuilder b1 = new ProgramBuilder();

			b1.size(1000);
			b1.loadi(1);
			b1.tax();
			int top = b1.txa();
			b1.storex(dataAdd);
			b1.incx();
			b1.txa();
			b1.subi(length + 1);
			b1.jneg(top);

			// Device
			b1.loadi(Machine.DISK2);
			b1.store(writeAdd);
			// Platter
			b1.loadi(6);
			b1.store(writeAdd + 1);
			// Platter Start
			b1.loadi(10 * i);
			b1.store(writeAdd + 2);
			// Length
			b1.loadi(21);
			b1.store(writeAdd + 3);
			// Memory
			b1.loadi(dataAdd + 1);
			b1.store(writeAdd + 4);

			b1.loadi(writeAdd);
			b1.syscall(SystemCall.WRITE);
			b1.syscall(SystemCall.WRITE);
			
			// Device
			b1.loadi(Machine.DISK);
			b1.store(writeAdd);
			// Platter
			b1.loadi(6);
			b1.store(writeAdd + 1);
			// Platter Start
			b1.loadi(10 * i);
			b1.store(writeAdd + 2);
			// Length
			b1.loadi(length);
			b1.store(writeAdd + 3);
			// Memory
			b1.loadi(dataAdd + 1);
			b1.store(writeAdd + 4);

			b1.loadi(writeAdd);
			b1.syscall(SystemCall.READ);
			
			 b1.load(501);
			 b1.syscall(SystemCall.WRITE_CONSOLE);
			 b1.load(502);
			 b1.syscall(SystemCall.WRITE_CONSOLE);
			 b1.load(503);
			 b1.syscall(SystemCall.WRITE_CONSOLE);
			 b1.load(504);
			 b1.syscall(SystemCall.WRITE_CONSOLE);
			 b1.load(505);



			Program p1 = b1.build();

			System.out.println(p1);

			Programs[i] = p1;
		}

		ProgramBuilder b2 = new ProgramBuilder();

		// Disk with buffer size 10
		b2.size(1000);
		b2.loadi(1);
		b2.tax();
		int top = b2.txa();
		b2.storex(dataAdd);
		b2.incx();
		b2.txa();
		b2.subi(length + 1);
		b2.jneg(top);
		
		//This program is placed outside of the loop, but is still scheduled at the same time as the others in an array.
		//The idea is to distinguish this program from the others with a platter start of 80 so that it'll illustrate starvation.
		//This program is supposed to get completed last because it is the furthest away on the disk and SSTF scheduling will complete
		//all programs closest to each other no matter their position on the queue.   
		
		b2.size(1000);
		b2.loadi(Machine.DISK2);
		b2.store(readAdd);
		// Platter
		b2.loadi(6);
		b2.store(readAdd + 1);
		// Platter Start
		b2.loadi(80);
		b2.store(readAdd + 2);
		// Length
		b2.loadi(length);
		b2.store(readAdd + 3);
		// Memory
		b2.loadi(501);
		b2.store(readAdd + 4);
		b2.loadi(readAdd);
		b2.syscall(SystemCall.WRITE);
		
		// b1.load(501);
		// b1.syscall(SystemCall.WRITE_CONSOLE);
		// b1.load(502);
		// b1.syscall(SystemCall.WRITE_CONSOLE);
		// b1.load(503);
		// b1.syscall(SystemCall.WRITE_CONSOLE);
		// b1.load(504);
		// b1.syscall(SystemCall.WRITE_CONSOLE);
		// b1.load(505);


		Program p2 = b2.build();
		System.out.println(p2);

		Programs[3] = p2;

		os.schedule(Programs);

	}
}

