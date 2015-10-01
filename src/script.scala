/* run with:
scala -cp "./lib/poi-3.12/poi-3.12-20150511.jar;./lib/poi-3.12/poi-ooxml-3.12-20150511.jar" src/script.scala
*/
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.DataFormatter
import java.io._
import scala.collection.JavaConversions._
import scala.util.Try

case class Matrix[T](vectors: Vector[Vector[T]]){
  val nrows = vectors.size
  lazy val ncols = vectors.map(_.size).max
  lazy val dim = (nrows, ncols)
  def row(r: Int): Vector[T] = vectors(r)
  def col(c: Int): Vector[T] = vectors.map(row => row(c))
  lazy val transpose = Matrix((0 until ncols).map(c => col(c)).toVector)
  def apply(x: Int, y: Int): T = vectors(x)(y)
  def get(x: Int, y: Int): Option[T] = Try(vectors(x)(y)).toOption
}

object Xls {

  def load(fileName: String, sheetNum: Int = 0): Matrix[String] = 
    loadFile(new File(fileName), sheetNum)

  def loadFile(file: File, sheetNum: Int = 0): Matrix[String] = {
    val wb = WorkbookFactory.create(file)
    val sheet = wb.getSheetAt(sheetNum); 
    val nrows = sheet.getLastRowNum
    val rowsMaybeNull: Vector[Row]  = (0 until nrows).map(r => sheet.getRow(r)).toVector
    val ncols = rowsMaybeNull.map(r => Option(r).map(_.getLastCellNum).getOrElse(0.toShort)).max
    val cellMatrix: Vector[Vector[Cell]] = rowsMaybeNull.map(r => 
      if (r != null) (0 until ncols).map(c => r.getCell(c)).toVector else Vector.fill(ncols)(null))
    val vectors: Vector[Vector[String]] = 
      cellMatrix.map(_.map(cell => cellToString(cell)))
    Matrix(vectors)
  } 

  lazy val dataFormatter = new DataFormatter(new java.util.Locale("en"))

  def cellToString(cell: Cell): String = 
    if (cell != null) dataFormatter.formatCellValue(cell) else ""
}

object Extract { 
  val DATA_DIR = "Data"
  def toInt(s: String): Int = Try(s.toDouble.toInt).getOrElse(0) 
  case class Quest(usageStr: String, meaningStr: String, other: String, synonym: String){
    val usage = toInt(usageStr)
    val meaning = toInt(meaningStr) 
    val agreement = usage + meaning
  }
  def ls(dir: String) = (new File(dir)).listFiles.toVector
  val files = ls(DATA_DIR)
  val data = files.map(Xls.loadFile(_))

  //def mapDataRow[T](f: (Xls.Matrix[String], Int) => T): Vector[Vector[T]] = 
    //data.map(m => (39 until 131).map(r => f(m,r)).toVector).toVector
  
  val names = data.map(_.apply(6,2))
  val idOfName = names.zipWithIndex.toMap
  val nameOfId = idOfName.collect { case (a,b) => (b,a) }
  val emails = data.map(_.apply(7,2))
  val n = files.size
  val (teach, develop, research) = (9, 10, 11)
  def yesOf(xs: Vector[String]) = xs.zipWithIndex.filter(_._1.toLowerCase == "yes").map(_._2)
  def background = for (i <- Seq(teach, develop, research)) yield {
     val ids = yesOf(data.map(_.apply(i, 4)))
     val question = data(0)(i,1) + " YES/NO"
     (question, ids)
  } 
  def printBackground = background.foreach(b => println(b._1 + "\n" + b._2.mkString(" ")))

  val teachers = data.map(_.apply(9,4))
  val developers = data.map(_.apply(10,4))
  val researchers = data.map(_.apply(11,4))

  val concepts = (39 until 131).map(r => data(0)(r,1)).toVector 
  val definitions = (39 until 131).map(r => data(0)(r,2)).toVector 
  val definitionOf = (concepts zip definitions).toMap

  val typedConcepts = (39 until 131).map(r => (data(0)(r,0), data(0)(r,1))).toVector
  val isEntity = typedConcepts.filter(_._1 == "Entity").map(_._2).toSet
  val isAttribute = typedConcepts.filter(_._1 == "Attribute").map(_._2).toSet
  val isRelation = typedConcepts.filter(_._1 == "Relation").map(_._2).toSet

  val quest = data.map(m => (39 until 131).map(r => Quest(m(r,3),m(r,4),m(r,5), m(r,6))))
  
  val agreeConcepts = (quest.map(_.map(_.agreement).toVector).transpose.map(_.sum) zip concepts).sortBy( pair => -pair._1)
  val agreeEntities = agreeConcepts.filter(pair => isEntity(pair._2))
  val useConcepts   = (quest.map(_.map(_.usage).toVector).transpose.map(_.sum) zip concepts).sortBy( pair => -pair._1)
  val useEntities = useConcepts.filter(pair => isEntity(pair._2))
  val meaningConcepts   = (quest.map(_.map(_.meaning).toVector).transpose.map(_.sum) zip concepts).sortBy( pair => -pair._1)
  val meaningEntities = useConcepts.filter(pair => isEntity(pair._2))
  
  val missingConcepts = data.flatMap(m => (m.col(1) zip  (m.col(0) zip m.col(2))).drop(133)).filter(_._1.size >0)
  
  def countRole(xs: Vector[String], answer: String) = xs.map(_.toLowerCase).count(_ == answer)

  val verdicts = Matrix(quest.map(_.map(q => (q.usage, q.meaning)).zip(concepts).toVector).toVector)
  val verdictsOfConcept = verdicts.transpose.vectors.map(v => v(0)._2 -> v.map(x => x._1)).toMap
  val usageVerdictsOfConcept = verdictsOfConcept.collect{case (c, v) => (c, v.map(_._1))}
  def countUsageVerdict(i: Int) = usageVerdictsOfConcept.collect{ case (c,v) => (c, v.count(_ == i))}
  def countUsageVerdictAtLest(i: Int) = usageVerdictsOfConcept.collect{ case (c,v) => (c, v.count(_ >= i))}
  def countVerdict(usage: Int, meaning: Int) = verdictsOfConcept.collect{ case (c,as)  => c -> as.count(a => a == (usage,meaning))}
  def countAtLeastVerdict(usage: Int, meaning: Int) = 
    verdictsOfConcept.collect{ case (c,as)  => c -> as.count(a => a._1 >= usage && a._2 >= meaning)}
  def subjectCountVerdict(usage: Int, meaning: Int) = concepts zip concepts.map(countVerdict(usage,meaning))

  def freq(xs: Map[String, Int]) = {
    val values = xs.values.toSet.toVector.sorted.reverse
    val keys = xs.keySet.toVector.sorted
    values.map(v => v -> keys.filter(k => xs(k) == v))
  }

  def freqOf(conceptTypeFilter: Set[String], countMap: Map[String, Int]) = 
    freq(countMap).collect{ case (i, cs) => (i,cs.filter(conceptTypeFilter))}
  
  def printFreq = {
    Seq((isEntity, "Entity"), (isRelation, "Relation"), (isAttribute, "Attribute")).foreach{_ match {
        case (conceptFilter, concept) => 

          println(s"\nNumber of subjects that for this $concept answered use >= 1")
          freqOf(conceptFilter, countUsageVerdictAtLest(1)) foreach (p => println(p._1 + " " + p._2.mkString(" ")))

          println(s"\nNumber of subjects that for this $concept answered use = 2")
          freqOf(conceptFilter, countUsageVerdictAtLest(2)) foreach (p => println(p._1 + " " + p._2.mkString(" ")))

          println(s"\nNumber of subjects that for this $concept answered (use, agree) = (2, 2)")
          freqOf(conceptFilter, countAtLeastVerdict(2, 2)) foreach (p => println(p._1 + " " + p._2.mkString(" ")))

          println(s"\nNumber of subjects that for this $concept answered (use, agree) = (1, 2)")
          freqOf(conceptFilter, countAtLeastVerdict(1, 2)) foreach (p => println(p._1 + " " + p._2.mkString(" ")))

    }}
  }

  def summary = {
    println("====== Data Summary =======")
    println(s"""  Number of subjects:    $n""")
    println(s"""  Number of teachers:    ${countRole(teachers,"yes")}""")
    println(s"""  Number of developers:  ${countRole(developers,"yes")}""")
    println(s"""  Number of researchers: ${countRole(researchers,"yes")}""")

    printBackground

    printFreq

    concepts.filter(isAttribute).map(c => (c,definitionOf(c))).foreach{case (c,d) => println(c+"&"+d+"\\\\")}
  }
}

Extract.summary

