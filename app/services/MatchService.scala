package services

import collection.breakOut

import anorm.NotAssigned
import org.joda.time.DateTime
import models._

object MatchService {

  // ===== Interface =====
  
  def captureMatch(games: Seq[Game])(implicit user: User) {
    val foosMatch = Match.create(Match(NotAssigned, new DateTime(), user.name, Match.Format.TwoOnTwo))
    games.map(game => Game.create(game.copy(matchId = foosMatch.id.get))) // This is ugly :(

    // All the games are supposed to have the same players, so we might as well use the first game
    val playerScores = games.head.players.map(player => player -> games.map(_.playerScore(player)).sum).toMap 
    val matchResults: List[MatchResult] = playerScores.map(playerWithScore => {
      val playerRank   = calculateRank(playerWithScore._2, playerScores.values)
      val playerResult = calculateResult(playerWithScore._2, playerScores.values)
      
      MatchResult(foosMatch.id.get, playerWithScore._1, playerResult, playerRank, playerWithScore._2)
    })(breakOut)

    matchResults.map(matchResult => MatchResult.create(matchResult))
    EloService.updateElo(matchResults)
  }
  
  // ===== Helpers =====
    
  private def calculateRank(playerScore: Int, scores: Iterable[Int]): Int = {
    // Rank falls by 1 for every player with a higher score (same score == same rank) 
    scores.count(_ > playerScore) + 1
  }
  
  // Visible for testing
  private[services] def calculateResult(playerScore: Int, scores: Iterable[Int]): MatchResult.Result.Value = playerScore match {
    case x if scores.count(_ == x) > 1                      => MatchResult.Result.NoResult     // Always no result if more than one person has this score
    case 6                                                  => MatchResult.Result.Winner       // Outright winner always has exactly 6 points
    case x if scores.count(_ < x) >= 2                      => MatchResult.Result.PseudoWinner // Not an outright winner, but has a better score than at least two other players
    case x if (scores.count(_ > x) == 3 && scores.max != 6) => MatchResult.Result.Loser        // All three other players have a better score, but none of them are an outright winner
    case x if (scores.count(_ > x) >= 2)                    => MatchResult.Result.PseudoLoser  // Not an outright loser, but has a worst score than at least two other players
  }
}