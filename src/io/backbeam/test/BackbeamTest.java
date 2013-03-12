package io.backbeam.test;

import io.backbeam.Backbeam;
import io.backbeam.BackbeamException;
import io.backbeam.BackbeamObject;
import io.backbeam.FetchCallback;
import io.backbeam.Location;
import io.backbeam.ObjectCallback;
import io.backbeam.OperationCallback;
import io.backbeam.Query;

import java.util.List;

public class BackbeamTest extends Test {
	
	public void configure() {
		before(new TestBlock() {
			public void run(DoneBlock done) {
				Backbeam.setHost("backbeamapps.dev");
				Backbeam.setPort(8079);
				Backbeam.setProject("callezeta");
				Backbeam.setEnvironment("dev");
				done.done();
			}
		});
		
		test("Test empty query", new TestBlock() {
			public void run(final DoneBlock done) {
				Backbeam.select("place").fetch(100, 0, new FetchCallback() {
					public void success(List<BackbeamObject> objects, int totalCount, boolean fromCache) {
						assertTrue(objects.size() == 0);
						done.done();
					}
					public void failure(BackbeamException exception) {
						System.out.println("Exception! "+exception);
						throw exception;
					}
				});
			}
		});
		
		test("Insert, update, refresh an object", new TestBlock() {
			public void run(final DoneBlock done) {
				Location location = new Location(41.640964, -0.8952422, "San Francisco Square, Zaragoza City");
				BackbeamObject object = new BackbeamObject("place");
				object.setString("name", "A new place");
				object.setLocation("location", location);
				object.save(new ObjectCallback() {
					public void success(BackbeamObject object) {
						assertTrue(object.getId() != null);
						assertTrue(object.getCreatedAt() != null);
						assertTrue(object.getUpdatedAt() != null);
						assertTrue(object.getCreatedAt().getTime() == object.getUpdatedAt().getTime());
						
						object.setString("name", "New name");
						object.setString("type", "Terraza");
						object.save(new ObjectCallback() {
							public void success(final BackbeamObject obj) {
								assertTrue(obj.getId() != null);
								assertTrue(obj.getCreatedAt() != null);
								assertTrue(obj.getUpdatedAt() != null);
								assertTrue(obj.getString("name").equals("New name"));
								
								BackbeamObject object = new BackbeamObject("place", obj.getId());
								object.refresh(new ObjectCallback() {
									public void success(BackbeamObject lastObject) {
										assertTrue(obj.getString("name").equals(lastObject.getString("name")));
										assertTrue(obj.getString("type").equals(lastObject.getString("type")));
										Location location = lastObject.getLocation("location");
										assertTrue(location != null);
										assertTrue(location.getAddress().equals("San Francisco Square, Zaragoza City"));
										assertTrue(location.getLatitude() == 41.640964);
										assertTrue(location.getLongitude() == -0.8952422);
										assertTrue(lastObject.getCreatedAt().equals(obj.getCreatedAt()));
										
										lastObject.setString("name", "Final name");
										lastObject.save(new ObjectCallback() {
											public void success(final BackbeamObject lastObject) {
												obj.setString("description", "Some description");
												obj.save(new ObjectCallback() {
													public void success(BackbeamObject obj) {
														assertTrue(obj.getString("name").equals(lastObject.getString("name")));
														assertTrue(obj.getString("type").equals(lastObject.getString("type")));
														done.done();
													}
													public void failure(BackbeamException exception) {
														System.out.println("Exception! "+exception);
														throw exception;
													}
												});
											}
											public void failure(BackbeamException exception) {
												System.out.println("Exception! "+exception);
												throw exception;
											}
										});
									}
									public void failure(BackbeamException exception) {
										System.out.println("Exception! "+exception);
										throw exception;
									}
								});
							}
							public void failure(BackbeamException exception) {
								System.out.println("Exception! "+exception);
								throw exception;
							}
						});
					}
					public void failure(BackbeamException exception) {
						System.out.println("Exception! "+exception);
						throw exception;
					}
				});
			}
		});
		
		test("Query with BQL and params", new TestBlock() {
			public void run(final DoneBlock done) {
				Query query = Backbeam.select("place");
				query.setQuery("where type=?", "Terraza");
				query.fetch(100, 0, new FetchCallback() {
					public void success(List<BackbeamObject> objects, int totalCount, boolean fromCache) {
						assertTrue(objects.size() == 1);
						BackbeamObject object = objects.get(0);
						assertTrue(object.getString("name").equals("Final name"));
						assertTrue(object.getString("description").equals("Some description"));
						done.done();
					}
					public void failure(BackbeamException exception) {
						System.out.println("Exception! "+exception);
						throw exception;
					}
				});
			}
		});

//		test("Push notifications", new TestBlock() {
//			public void run(final DoneBlock done) {
//			}
//		});

		test("Register, login", new TestBlock() {
			public void run(final DoneBlock done) {
				BackbeamObject object = new BackbeamObject("user");
				object.setString("email", "gimenete@gmail.com");
				object.setString("password", "123456");
				object.save(new ObjectCallback() {
					public void success(BackbeamObject object) {
						assertTrue(Backbeam.currentUser() != null);
						assertTrue(Backbeam.currentUser().getId().equals(object.getId()));
						assertTrue(Backbeam.currentUser().getString("email").equals(object.getString("email")));
						assertTrue(Backbeam.currentUser().getString("password") == null);
						assertTrue(object.getString("password") == null);
						
						Backbeam.logout();
						assertTrue(Backbeam.currentUser() == null);
						Backbeam.login("gimenete@gmail.com", "123456", new ObjectCallback() {
							public void success(BackbeamObject object) {
								assertTrue(Backbeam.currentUser() != null);
								assertTrue(Backbeam.currentUser().getId().equals(object.getId()));
								assertTrue(Backbeam.currentUser().getString("email").equals(object.getString("email")));
								assertTrue(Backbeam.currentUser().getString("password") == null);
								assertTrue(object.getString("password") == null);
								
								done.done();
							}
							public void failure(BackbeamException exception) {
								System.out.println("Exception! "+exception);
								throw exception;
							}
						});
					}
					public void failure(BackbeamException exception) {
						System.out.println("Exception! "+exception);
						throw exception;
					}
				});
			}
		});

		test("User already registered", new TestBlock() {
			public void run(final DoneBlock done) {
				BackbeamObject object = new BackbeamObject("user");
				object.setString("email", "gimenete@gmail.com");
				object.setString("password", "123456");
				object.save(new ObjectCallback() {
					public void success(BackbeamObject object) {
						assertTrue(false);
					}
					public void failure(BackbeamException exception) {
						assertTrue(exception != null);
						done.done();
					}
				});
			}
		});

		test("Unsuccessfull login. User not found", new TestBlock() {
			public void run(final DoneBlock done) {
				Backbeam.login("foo@example.com", "xxxx", new ObjectCallback() {
					public void success(BackbeamObject object) {
						assertTrue(false);
					}
					public void failure(BackbeamException exception) {
						assertTrue(exception != null);
						done.done();
					}
				});
			}
		});

		test("Unsuccessfull login. Wrong password", new TestBlock() {
			public void run(final DoneBlock done) {
				Backbeam.login("gimenete@gmail.com", "xxxx", new ObjectCallback() {
					public void success(BackbeamObject object) {
						assertTrue(false);
					}
					public void failure(BackbeamException exception) {
						assertTrue(exception != null);
						done.done();
					}
				});
			}
		});

		test("Request password reset", new TestBlock() {
			public void run(final DoneBlock done) {
				Backbeam.requestPasswordReset("gimenete@gmail.com", new OperationCallback() {
					public void success() {
						done.done();
					}
					@Override
					public void failure(BackbeamException exception) {
					}
				});
			}
		});
	}

}
