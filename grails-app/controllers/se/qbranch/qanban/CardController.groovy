package se.qbranch.qanban

import grails.converters.*
import org.codehaus.groovy.grails.plugins.springsecurity.Secured

@Secured(['IS_AUTHENTICATED_FULLY'])
class CardController {

    def eventService
    def ruleService
    def securityService


    // Create
    @Secured(['ROLE_QANBANADMIN'])
    def create = {

        if ( !params.boardId )
            return render(status: 400, text: "The parameter 'boardId' must be specified")

        CardEventCreate createEvent = createCardEventCreate(params)      
        eventService.persist(createEvent)
        renderCreateResult(createEvent)
    }

    private CardEventCreate createCardEventCreate(params){
        def event = new CardEventCreate(params)
        event.user = securityService.getLoggedInUser()
        event.phaseDomainId = Board.get(params.boardId).phases[0].domainId
        return event
    }

    private renderCreateResult(createEvent){
        withFormat{
            html{
                def board = createEvent.board
                return render(template:'cardForm',model:[createEvent:createEvent, boardInstance: board])
            }
            js{
                return render ( [ cardInstance : createEvent.card ] as JSON)
            }
            xml{
                return render ( [ cardInstance : createEvent.card ] as XML)
            }
        }
    }


    // Retrive

    def show = {

        if( !params.id )
            return render(status: 400, text: "You need to specify an id")

        if( !Card.exists(params.id) )
            return render(status: 404, text: "Card with id $params.id not found")

        def card = Card.get(params.id)

        withFormat {
            html{
                return render (template: 'card', bean: card)
            }
            js{
                return render ( [cardInstance : card] as JSON )
            }
            xml{
                return render ( [cardInstance : card] as XML )
            }
        }
    }
    
    def form = {

        if( !params.id )
            return renderFormCreateMode(params)

        if( !Card.exists(params.id) )
            return render(status: 404, text: "Card with id $params.id not found")

        if( params.newPos && params.newPhase )
            return renderFormMoveMode(params)

        return renderFormEditMode(params)
        
    }

    private def renderFormCreateMode(params){
        if ( !params.'board.id' ) return render(status: 400, text: "The parameter 'boardId' must be specified")
        def board = Board.get(params.'board.id')
        def users = User.list();
        return render(template:'cardForm',model:[ boardInstance: board, userList: users])
    }

    private def renderFormMoveMode(params){
        def moveEvent = new CardEventMove()
        moveEvent.card =  Card.get(params.id)
        return render(template:'cardForm', model:[ newPhase: params.newPhase, newPos: params.newPos, moveEvent: moveEvent, userList: User.list(), loggedInUser: securityService.getLoggedInUser() ])
    }

    private def renderFormEditMode(params){
        def card = Card.get(params.id)
        def updateEvent = new CardEventUpdate()
        updateEvent.card = card
        
        def users = User.list()

        return render(template:'cardForm',model:[ updateEvent: updateEvent , userList: users])
    }

    // Update
    def update = { SetAssigneeCommand sac ->
        
        CardEventUpdate updateEvent = new CardEventUpdate()
        updateEvent.card = Card.get(params.id)
        updateEvent.properties = params
        updateEvent.user = securityService.getLoggedInUser()

        if( sac.hasErrors() )
            return render(status: 400, text: "Bad request; The assignee you tried to set is invalid")

        CardEventSetAssignee assigneeEvent = createCardEventSetAssignee(sac)

        // TODO: Do it like this? eventService.persist(updateEvent,assigneeEvent)

        eventService.persist(updateEvent)
        eventService.persist(assigneeEvent)

        withFormat{
            html{
                def users = User.list();
                return render (template: 'cardForm', model:[updateEvent:updateEvent, userList: users])
            }
            js{
                return render ( [cardInstance : updateEvent.card] as JSON )
            }
            xml{
                return render ( [cardInstance : updateEvent.card] as XML )
            }
        }

    }

    private CardEventSetAssignee createCardEventSetAssignee(cmd) {

        def event = new CardEventSetAssignee(
            card: cmd.card,
            user: securityService.getLoggedInUser(),
            newAssignee: cmd.assignee)

        return event
    }

    // Delete

    @Secured(['ROLE_QANBANADMIN'])
    def delete = {
        println "del"
        if( !params.id )
            return render(status: 400, text: "You need to specify a card")
        if( !Card.exists(params.id) )
            return render(status: 404, text: "Card with id $params.id not found")

         def deleteEvent = new CardEventDelete()
         deleteEvent.user = securityService.getLoggedInUser()
         deleteEvent.card = Card.get(params.id)
         
         eventService.persist(deleteEvent)
         println 'postPersist - err: ' + deleteEvent.hasErrors()
         deleteEvent.errors.getAllErrors().each{
             println it
         }
         if( !deleteEvent.hasErrors() )
            return render(status: 200, text: "Phase with id $params.id deleted")

         return render(status: 503, text: "Server error: card delete error #173")
    }

    // Move

    def move = { MoveCardCommand mcc, SetAssigneeCommand sac ->

        if( mcc.hasErrors() || sac.hasErrors() || !ruleService.isMoveLegal(mcc.oldPhaseEntity,mcc.newPhaseEntity) ){
            return render(status: 400, text: "Bad Request")
        } else {
            def saEvent = createCardEventSetAssignee(sac)
            def mcEvent = null

            if( isMovingToANewPosition(mcc) ){
                mcEvent = createCardEventMove(mcc)
            }

            eventService.persist(mcEvent)
            eventService.persist(saEvent)

            return render(template:'cardForm', model:[ newPhase: params.newPhase, newPos: params.newPos, moveEvent: mcEvent, userList: User.list(), loggedInUser: securityService.getLoggedInUser() ])

        }
    }

    private CardEventMove createCardEventMove(cmd) {
        def user = User.get(params.user) // TODO: Fixa så att den inloggade usern kommer med anropet
        def cardEventMove = new CardEventMove(
            newPhase: cmd.newPhaseEntity,
            newCardIndex: cmd.newPos,
            card: cmd.card,
            user: securityService.getLoggedInUser())
        return cardEventMove
    }

    private boolean isMovingToANewPosition(MoveCardCommand cmd) {
        def initialCardIndex = cmd.card.phase.cards.indexOf(cmd.card)
        def initialPhase = cmd.card.phase

        if(initialCardIndex == cmd.newPos && initialPhase.equals(cmd.newPhaseEntity))
            return false
            
        return true
    }
    
}


class MoveCardCommand {

    static constraints = {
        id(min: 0, nullable: false, validator:{ val, obj ->
                Card.exists(val)
            })
        newPos(min: 0, nullable: false)
        newPhase(min: 0, nullable: false, validator:{val, obj ->
                Phase.exists(val)
            })
    }

    static mapping = {
        version false
    }

    Integer id
    Integer newPos
    Integer newPhase

    def getCard() {
        Card.get(id)
    }

    def getOldPhaseEntity() {
        Card.get(id).phase
    }
    def getNewPhaseEntity() {
        Phase.get(newPhase)
    }

}

class SetAssigneeCommand {

    static constraints = {
        assigneeId(min: 0, nullable: true, validator:{ val, obj ->
                !val || User.exists( val )
            })
        id(min: 0, nullable: false, validator:{ val, obj ->
                Card.exists(val)
            })

    }
    Integer assigneeId
    Integer id

    def getAssignee() {
        User.get(assigneeId)
    }

    def getCard() {
        Card.get(id)
    }
}
