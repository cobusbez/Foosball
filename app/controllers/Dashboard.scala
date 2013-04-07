package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.Json

import anorm.NotAssigned

import domain._
import models._
import views._
import services._
import util.security._

object Dashboard extends Controller with Secured {
  
  // ===== Forms =====
  
  val captureMatchForm = Form(
    "results" -> nonEmptyText
  ) 

  // ===== Actions =====
  
  def show = SecuredAction { implicit request => {
    val users = User.all
    
    val unconfirmedMatches = Match.findUnconfirmedFor(request.session.get("username").get)
    val matchesWithResults = unconfirmedMatches.map(foosMatch => MatchWithResults(foosMatch, MatchResult.findByMatch(foosMatch.id.get)))
    
    val playerRankings = RankingService.loadCurrentRankings.sortBy(_.rank)
    
    Ok(html.dashboard.index(users, matchesWithResults, playerRankings))
  }}
  
  def captureMatch = SecuredAction { implicit request => {
    val resultsJson = captureMatchForm.bindFromRequest.get
    val results = Json.parse(resultsJson)
    
    // TODO: Should probably do this with Reads
    val games = for (index <- 0 until 3) yield {
      val winner1 = ((results \ "games")(index) \ "winners")(0).as[String]
      val winner2 = ((results \ "games")(index) \ "winners")(1).as[String]
      
      val loser1 = ((results \ "games")(index) \ "losers")(0).as[String]
      val loser2 = ((results \ "games")(index) \ "losers")(1).as[String]
      
      val result = (((results \ "games")(index)) \ "result").as[String]
          
      Game(NotAssigned, 0, winner1, winner2, loser1, loser2, result)
    } 
    
    MatchService.captureMatch(games, request.session.get("username").get)
           
    Redirect(routes.Dashboard.show).flashing("success" -> Messages("match.capture.success"))
  }}
  
  def confirmMatch(matchId: Long) = SecuredAction { implicit request => {
    MatchService.confirmMatch(matchId, request.session.get("username").get)
    val playerRankings = RankingService.loadCurrentRankings.sortBy(_.rank)
    Ok(html.tags.rankingTable(playerRankings))
  }}
}