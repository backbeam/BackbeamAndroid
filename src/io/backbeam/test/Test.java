package io.backbeam.test;

import java.util.ArrayList;
import java.util.List;

abstract class TestBlock {
	
	public abstract void run(DoneBlock done);

}

public class Test {
	
	private TestBlock empty = new TestBlock() {
		public void run(DoneBlock done) {
			done.done();
		}
	};
	
	private TestBlock before = empty;
	private TestBlock after = empty;
	private TestBlock beforeEach = empty;
	private TestBlock afterEach = empty;
	private DoneBlock finish;
	private List<TestBlock> tests = new ArrayList<TestBlock>();
	private List<String> testNames = new ArrayList<String>();
	private int i;
	
	public void before(TestBlock before) {
		this.before = before;
	}
	
	public void after(TestBlock after) {
		this.after = after;
	}
	
	public void beforeEach(TestBlock beforeEach) {
		this.beforeEach = beforeEach;
	}
	
	public void afterEach(TestBlock afterEach) {
		this.afterEach = afterEach;
	}
	
	public void test(String message, TestBlock test) {
		tests.add(test);
		testNames.add(message);
	}
	
	private void runNext() {
		if (i < tests.size()) {
			System.out.println("Running "+testNames.get(i));
			beforeEach.run(new DoneBlock() {
				public void done() {
					TestBlock block = tests.get(i);
					block.run(new DoneBlock() {
						public void done() {
							i++;
							afterEach.run(new DoneBlock() {
								public void done() {
									runNext();
								}
							});
						}
					});
				}
			});
		} else {
			after.run(new DoneBlock() {
				public void done() {
					finish.done();
				}
			});
		}
	}
	
	public void run(DoneBlock done) {
		i = 0;
		finish = done;
		before.run(new DoneBlock() {
			public void done() {
				runNext();
			}
		});
	}
	
	public void assertTrue(boolean condition) {
		if (!condition) {
			throw new RuntimeException("Assert failed");
		}
	}
	
	public void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new RuntimeException(message);
		}
	}

}
