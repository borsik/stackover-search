final case class Item(tags: Seq[String], is_answered: Boolean)
final case class ApiResponse(items: Seq[Item])
final case class Statistics(total: Int, answered: Int)
