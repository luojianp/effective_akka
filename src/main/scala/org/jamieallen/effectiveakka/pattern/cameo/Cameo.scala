package org.jamieallen.effectiveakka.pattern.cameo

import java.util.concurrent.TimeoutException
import scala.concurrent.{ ExecutionContext, Promise }
import scala.concurrent.duration._
import akka.actor._
import scala.math.BigDecimal.int2bigDecimal
import org.jamieallen.effectiveakka.common._

class AccountBalanceResponseHandler(savingsAccounts: ActorRef, checkingAccounts: ActorRef,
  moneyMarketAccounts: ActorRef, originalSender: ActorRef) extends Actor {

  val promisedResult = Promise[AccountBalances]()
  var checkingBalances, savingsBalances, mmBalances: Option[List[(Long, BigDecimal)]] = None
  def receive = {
    case CheckingAccountBalances(balances) =>
      checkingBalances = balances
      collectBalances
    case SavingsAccountBalances(balances) =>
      savingsBalances = balances
      collectBalances
    case MoneyMarketAccountBalances(balances) =>
      mmBalances = balances
      collectBalances
  }

  def collectBalances = (checkingBalances, savingsBalances, mmBalances) match {
    case (Some(c), Some(s), Some(m)) =>
      if (promisedResult.trySuccess(AccountBalances(checkingBalances, savingsBalances, mmBalances)))
        sendResults
    case _ =>
  }

  implicit val ec: ExecutionContext = context.dispatcher
  def sendResults = {
    originalSender ! ((promisedResult.future.map(x => x)) recover { case t: TimeoutException => t })
    context.system.stop(self)
  }

  context.system.scheduler.scheduleOnce(250 milliseconds) {
    if (promisedResult.tryFailure(new TimeoutException))
      sendResults
  }
}

class AccountBalanceRetriever(savingsAccounts: ActorRef, checkingAccounts: ActorRef, moneyMarketAccounts: ActorRef) extends Actor {
  def receive = {
    case GetCustomerAccountBalances(id) =>
      val originalSender = sender
      val handler = context.actorOf(Props(new AccountBalanceResponseHandler(savingsAccounts, checkingAccounts, moneyMarketAccounts, originalSender)))
      savingsAccounts.tell(GetCustomerAccountBalances(id), handler)
      checkingAccounts.tell(GetCustomerAccountBalances(id), handler)
      moneyMarketAccounts.tell(GetCustomerAccountBalances(id), handler)
  }
}