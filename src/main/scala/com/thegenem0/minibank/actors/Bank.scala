package com.thegenem0.minibank.actors

import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.util.Timeout

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.util.Failure

object Bank {

  import PersistentBankAccount.Command._
  import PersistentBankAccount.Response._
  import PersistentBankAccount.Command

  sealed trait Event

  case class BankAccountCreated(id: String) extends Event

  case class State(accounts: Map[String, ActorRef[Command]])

  def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = (state, command) =>
    command match {
      case createCommand@CreateBankAccount(_, _, _, _) =>
        val id = UUID.randomUUID().toString
        val newBankAccount = context.spawn(PersistentBankAccount(id), id)
        Effect
          .persist(BankAccountCreated(id))
          .thenReply(newBankAccount)(+ => createCommand)

      case updateCommand@UpdateBalance(id, _, _, replyTo) =>
        state.accounts.get(id) match {
          case Some(account) =>
            Effect.reply(account)(updateCommand)

          case None =>
            Effect.reply(replyTo)(BankAccountBalanceUpdatedResponse(Failure(new RuntimeException("Bank account not found"))))
        }
      case getCommand@GetBankAccount(id, replyTo) =>
        state.accounts.get(id) match {
          case Some(account) =>
            Effect.reply(account)(getCommand)
          case None =>
            Effect.reply(replyTo)(GetBankAccountResponse(None))
        }
    }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) =>
    event match {
      case BankAccountCreated(id) =>
        val account = context.child(id)
          .getOrElse(context.spawn(PersistentBankAccount(id), id))
          .asInstanceOf[ActorRef[Command]]

        state.copy((state.accounts + (id -> account)))
    }

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId("bank"),
      emptyState = State(Map()),
      commandHandler = commandHandler(context),
      eventHandler = eventHandler(context)
    )
  }
}

object BankPlayground {

  import PersistentBankAccount.Command._
  import PersistentBankAccount.Response._
  import PersistentBankAccount.Response

  def main(args: Array[String]): Unit = {
    val rootBehavior: Behavior[NotUsed] = Behaviors.setup { context =>
      val bank = context.spawn(Bank(), "bank")
      val logger = context.log

      val responseHandler = context.spawn(Behaviors.receiveMessage[Response] {
        case BankAccountCreatedResponse(id) =>
          logger.info(s"successfully created bank account $id")
          Behaviors.same
        case GetBankAccountResponse(maybeBankAccount) =>
          logger.info(s"Account details: $maybeBankAccount")
          Behaviors.same
      }, "replyHandler")

      // ask pattern
      import akka.actor.typed.scaladsl.AskPattern._
      import scala.concurrent.duration._
      implicit val timeout: Timeout = Timeout(2.seconds)
      implicit val scheduler: Scheduler = context.system.scheduler
      implicit val ec: ExecutionContext = context.executionContext

      // test 1
            bank ! CreateBankAccount("daniel", "USD", 10, responseHandler)

      // test 2
      //      bank ! GetBankAccount("replaceWithYourUuidHere", responseHandler)

      Behaviors.empty
    }

    val system = ActorSystem(rootBehavior, "BankDemo")
  }
}
