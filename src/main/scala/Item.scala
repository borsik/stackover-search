case class Item(tags: Seq[String], is_answered: Boolean)
case class ApiResponse(items: Seq[Item])
case class Statistics(total: Int, answered: Int)
