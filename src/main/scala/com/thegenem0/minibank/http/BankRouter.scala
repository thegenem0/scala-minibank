package com.thegenem0.minibank.http

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import com.thegenem0.minibank.actors.PersistentBankAccount.Command._
import com.thegenem0.minibank.actors.PersistentBankAccount.{Command, Response}
import com.thegenem0.minibank.actors.PersistentBankAccount.Response._
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

case class BankAccountCreationRequest(user: String, currency: String, balance: Double) {
  def toCommand(replyTo: ActorRef[Response]): Command = CreateBankAccount(user, currency, balance, replyTo)
}

case class BankAccountUpdateRequest(currency: String, amount: Double)

case class FailureResponse(reason: String)

class BankRouter(bank: ActorRef[Command])(implicit system: ActorSystem[_]) {
  implicit val timeout: Timeout = Timeout(5.seconds)

  private def createBankAccount(req: BankAccountCreationRequest): Future[Response] =
    bank.ask(replyTo => req.toCommand(replyTo))

  private def getBankAccount(id: String): Future[Response] =
    bank.ask(replyTo => GetBankAccount(id, replyTo))

  private def updateBankAccount(id: String, req: BankAccountUpdateRequest): Future[Response] =
    bank.ask(replyTo => UpdateBalance(id, req.currency, req.amount, replyTo))

  val routes =
    pathPrefix("bank") {
      pathEndOrSingleSlash {
        post {
          entity(as[BankAccountCreationRequest]) { req =>
            onSuccess(createBankAccount(req)) {
              case BankAccountCreatedResponse(id) =>
                respondWithHeader(Location(s"/bank/$id")) {
                  complete(StatusCodes.Created, id)
                }
            }
          }
        }
      } ~
        path(Segment) { id =>
          get {
            onSuccess(getBankAccount(id)) {
              case GetBankAccountResponse(Some(account)) =>
                complete(account)
              case GetBankAccountResponse(None) =>
                complete(
                  StatusCodes.NotFound,
                  FailureResponse(s"Bank account with id $id not found")
                )
            }
          } ~
            put {
              entity(as[BankAccountUpdateRequest]) { req =>
                onSuccess(updateBankAccount(id, req)) {
                  case BankAccountBalanceUpdatedResponse(Success(account)) =>
                    complete(account)
                  case BankAccountBalanceUpdatedResponse(Failure(ex)) =>
                    complete(
                      StatusCodes.BadRequest,
                      FailureResponse(ex.getMessage)
                    )
                }
              }
            }
        }
    }
}