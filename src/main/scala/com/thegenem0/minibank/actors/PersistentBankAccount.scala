package com.thegenem0.minibank.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.util.{Failure, Success, Try}

object PersistentBankAccount {

  sealed trait Command

  object Command {
    case class CreateBankAccount(user: String, currency: String, initialBalance: Double, replyTo: ActorRef[Response])
      extends Command

    case class UpdateBalance(id: String, currency: String, amount: Double, replyTo: ActorRef[Response])
      extends Command

    case class GetBankAccount(id: String, replyTo: ActorRef[Response]) extends Command
  }

  private trait Event

  private case class BankAccountCreated(bankAccount: BankAccount) extends Event

  private case class BalanceUpdated(amount: Double) extends Event

  case class BankAccount(id: String, user: String, currency: String, balance: Double)

  sealed trait Response

  object Response {
    case class BankAccountCreatedResponse(id: String) extends Response

    case class BankAccountBalanceUpdatedResponse(maybeBankAccount: Try[BankAccount]) extends Response

    case class GetBankAccountResponse(maybeBankAccount: Option[BankAccount]) extends Response
  }

  import Command._
  import Response._

  private val commandHandler: (BankAccount, Command) => Effect[Event, BankAccount] = (state, command) =>
    command match {
      case CreateBankAccount(user, currency, initialBalance, bank) =>
        val id = state.id
        Effect
          .persist(BankAccountCreated(BankAccount(id, user, currency, initialBalance)))
          .thenReply(bank)(_ => BankAccountCreatedResponse(id))

      case UpdateBalance(_, _, amount, bank) =>
        val newBalance = state.balance + amount
        if (newBalance < 0) {
          Effect.reply(bank)(BankAccountBalanceUpdatedResponse(
            Failure(new RuntimeException("Balance cannot be negative")
            )))
        } else {
          Effect
            .persist(BalanceUpdated(newBalance))
            .thenReply(bank)(newState => BankAccountBalanceUpdatedResponse(Success(newState)))
        }

      case GetBankAccount(_, bank) =>
        Effect.reply(bank)(GetBankAccountResponse(Some(state)))
    }

  private val eventHandler: (BankAccount, Event) => BankAccount = (state, event) =>
    event match {
      case BankAccountCreated(bankAccount) =>
        bankAccount

      case BalanceUpdated(amount) =>
        state.copy(balance = state.balance + amount)
    }

  def apply(id: String): Behavior[Command] =
    EventSourcedBehavior[Command, Event, BankAccount](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = BankAccount(id, "", "", 0.0),
      commandHandler = commandHandler,
      eventHandler = eventHandler
    )

}
