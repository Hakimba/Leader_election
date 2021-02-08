package upmc.akka.leader

import java.util
import java.util.Date

import akka.actor._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

abstract class Tick
case class CheckerTick () extends Tick

class CheckerActor (val id:Int, val terminaux:List[Terminal], electionActor:ActorRef) extends Actor {

     var time : Int = 200
     val father = context.parent

     var nodesAlive:List[Int] = List()
     // on stocke les nodes qui etaient vivant a "l'instant" précédent
     // pour les comparer aux nodes vivant a "l'instant" courant
     // et ainsi déduire quels nodes sont mort
     var nodesWasAlive:List[Int] = List()
     var datesForChecking:List[Date] = List()
     var lastDate:Date = null

     var leader : Int = -1

    def receive = {

         // Initialisation
        case Start => {
             self ! CheckerTick
        }

        // A chaque fois qu'on recoit un Beat : on met a jour la liste des nodes
        case IsAlive (nodeId) => {
             nodesAlive.find(_.==(nodeId)) match {
                  case None => {
                       nodesAlive = nodeId :: nodesAlive
                  }
                  case _ =>
             }
        }

        case IsAliveLeader (nodeId) => {
            leader = nodeId
            nodesAlive.find(_.==(nodeId)) match {
                    case None => {
                         nodesAlive = nodeId :: nodesAlive
                    }
                    case _ =>
               }

        }

        // A chaque fois qu'on recoit un CheckerTick : on verifie qui est mort ou pas
        // Objectif : lancer l'election si le leader est mort
        case CheckerTick => {
            context.system.scheduler.scheduleOnce(time.milliseconds, self, CheckerTick)
           // par construction, tout les noeuds n'ont pas la liste des noeuds dans le meme ordre, donc on doit trier
          nodesAlive = nodesAlive.sorted
          for (node <- nodesWasAlive){
               nodesAlive.find(_.==(node)) match {
                    case None => {
                         // Le noeud est mort, il etais vivant avant, et ne l'est pas dans la liste des noeuds vivant courant
                         father ! Message ("node "+node + " is dead")
                         if(node == leader){
                              father ! Message ("========= Leader is dead -> NEW ELECTION ========")
                              electionActor ! StartWithNodeList (nodesAlive)
                         }
                    }
                    case _ =>
               }
          }
          nodesWasAlive = nodesAlive
          // on vide les noeuds vivant, pour pouvoir comparer la prochaine liste avec l'ancienne
          // et savoir qui est mort
          nodesAlive = List()
        }

    }
}
