package se.qbranch.qanban

import grails.test.*
import org.grails.plugins.springsecurity.service.AuthenticateService

class UserEventCreateTests extends GrailsUnitTestCase {

  def authMock
  
  protected void setUp() {
    super.setUp()
    mockDomain(User)
    mockDomain(Role)
    mockDomain(UserEventCreate)

    authMock = mockFor(AuthenticateService)
    authMock.demand.static.encodePassword(1..2) { pass -> pass }


  }

  protected void tearDown() {
    super.tearDown()
  }

  void testCreatingAUser() {
    assertEquals 0, User.list().size()
    def username = "opsmrkr01"
    def userRealname = "mr. Krister"
    def email = "mr.krister@mail.com"
    def passwd ="pass"
    def event = new UserEventCreate(username: username, userRealName: userRealname, email: email, enabled: true, passwd: passwd, passwdRepeat: passwd)
    event.authenticateService = authMock.createMock()

    event.beforeInsert()
    if( event.save() ){
      event.process()
    }

    def user = event.user
    assertFalse "There should not be any errors", event.hasErrors()
    assertEquals event.username, user.username
    assertEquals event.domainId, user.domainId
    assertNotNull "The user should have a id", user.id
    assertEquals 1, User.list().size()
  }

  void testCreatingAUserFromAUserObj(){
    assertEquals 0, User.list().size()

    def user = new User(username: "opsmrkr01",userRealName: "Mister Krister",email:"mr.kr@gmail.com",enabled: true,passwd: 'pass',passwdRepeat: 'pass')
    def event = new UserEventCreate(user: user)
    event.authenticateService = authMock.createMock()

    event.populateFromUser()

    assertEquals user.username, event.username
    assertEquals user.userRealName, event.userRealName
    assertEquals user.email, event.email
    assertEquals user.enabled, event.enabled

    event.beforeInsert()
    if( event.save() ) {
      event.process()
    }

    def userAfter = event.user
    assertFalse "There should not be any errors", event.hasErrors()
    assertEquals event.username, userAfter.username
    assertEquals event.domainId, userAfter.domainId
    assertNotNull "The user should have a id", userAfter.id
    assertEquals 1, User.list().size()
  }

  void testCreatingAUserWithPass(){
    def initNoOfUsers = User.list().size()
    def user = new User(username: "opsmrkr01",userRealName: "Mister Krister",email:"mr.kr@gmail.com",enabled: true,passwd:"pass1")
    def event = new UserEventCreate(user:user)
    event.authenticateService = authMock.createMock()

    event.populateFromUser()
    event.passwdRepeat = "pass1"

    event.beforeInsert()

    if(event.save()){
      event.process()
    }

    def userAfter = event.user
    assertFalse "There should not be any errors", event.hasErrors()
    assertEquals event.username, userAfter.username
    assertEquals event.domainId, userAfter.domainId
    assertNotNull "The user should have a id", userAfter.id
    assertEquals 1, User.list().size()

  }

  void testCreateWithInconsistentPasswords(){
    def initNoOfUsers = User.list().size()
    def user = new User(username: "opsmrkr01",userRealName: "Mister Krister",email:"mr.kr@gmail.com",enabled: true,passwd:"pass1")
    def event = new UserEventCreate(user:user)
    event.authenticateService = authMock.createMock()

    event.populateFromUser()
    event.passwdRepeat = "pass2"

    event.beforeInsert()

    if(event.save()){
      event.process()
    }

    def userAfter = event.user
    assertEquals 1 , event.errors.allErrors.size()
    assertNull "The user should not have a id", userAfter.id
    assertEquals initNoOfUsers, User.list().size()
  }

  void testCreateWithoutPasswords(){
    def initNoOfUsers = User.list().size()
    def user = new User(username: "opsmrkr01",userRealName: "Mister Krister",email:"mr.kr@gmail.com",enabled: true)
    def event = new UserEventCreate(user:user)
    event.authenticateService = authMock.createMock()
    event.populateFromUser()

    event.beforeInsert()

    if(event.save()){
      event.process()
    }

    def userAfter = event.user
    assertEquals 1 , event.errors.allErrors.size()
    assertNull "The user should not have a id", userAfter.id
    assertEquals initNoOfUsers, User.list().size()
  }
}
