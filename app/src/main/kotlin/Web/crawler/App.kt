/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package Web.crawler

import ca.rmen.porterstemmer.PorterStemmer
import org.jsoup.Jsoup
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Matcher
import java.util.regex.Pattern

class MyLock
{
    private val lock = Object()
    private val count = AtomicInteger(0)
    fun waitForTasks()
    {
        synchronized(lock)
        {
            lock.wait()
        }
    }
    fun beginTask()
    {
        count.incrementAndGet()
    }

    fun finishTask()
    {
        synchronized(count)
        {
            count.decrementAndGet()
            if(count.get() < 0)
            {
                synchronized(lock)
                {
                    lock.notify()
                }
            }
        }
    }
}


class BasicWebCrawler(val maxCycles: Int) {

    private val visitedLinks: MutableSet<String> = mutableSetOf()

    //private val toVisit:Queue<String> = LinkedList()
    private val stemmer: PorterStemmer = PorterStemmer()
    val invertedIndex: MutableMap<String, MutableSet<Pair<String, Int>>> = sortedMapOf()
    private val stopWords: Set<String> = File("StopWords.txt").readLines().toSet()
    private val executor = Executors.newFixedThreadPool(8)
    @Volatile
    private var currentCycle:Int = 0

    private val myLock = MyLock()

    fun addToIndex(word: String, position: Int, link: String) {
        synchronized(invertedIndex)
        {
            invertedIndex[word] = invertedIndex.getOrDefault(word, mutableSetOf())
                    .apply {
                        add(link to position)
                    }
        }
    }

    fun start(link: String)
    {
        visitedLinks.add(link)
        executor.execute { crawl(link) }
        myLock.waitForTasks()
        executor.shutdown()
        println("Done")
    }

    private fun crawl(link: String) {


        //1. Fetch the HTML page
        val document = try {
            Jsoup.connect(link).get()
        }
        catch (e: Exception)
        {
            myLock.finishTask()
            return
        }

        //2. Parse the HTML to extract the links
        val linksOnPage = document.select("a[href]")

        //3. Get all the text on the page
        val bodyText = document.select("body").text()

        val tokenizer: Pattern = Pattern.compile("[a-zA-Z]+")

        val matcher: Matcher = tokenizer.matcher(bodyText)

        var position: Int = 0

        while (matcher.find()) {
            val word = matcher.group().toLowerCase()
            //println(word)
            val stem = stemmer.stemWord(word)
            //println(stem)

            if (!stopWords.contains(stem)) {
                addToIndex(stem, position, link)
            }

            position++
        }
        println("Processed link: $link")
        //4. For each extracted URL add it to the toVisit list
        for (newLink in linksOnPage) {
            val linkUrl = newLink.attr("abs:href")

            synchronized(visitedLinks)
            {
                if (visitedLinks.add(linkUrl))
                {
                    synchronized(currentCycle)
                    {
                        if(currentCycle < maxCycles)
                        {
                            currentCycle++
                            myLock.beginTask()
                            //println("Enqueued link: $linkUrl")
                            executor.execute {crawl(linkUrl)}
                        }
                    }
                }
            }
        }

        myLock.finishTask()

    }
}



fun main(args: Array<String>) {
    val wrrryyy = "https://tender-shannon-bc1412.netlify.app/"
    val cnn = "http://www.cnn.com"
    val google = "http://www.google.com"
    val crawler = BasicWebCrawler(30)
    crawler.start(cnn)

    val result = crawler.invertedIndex

    val outputFile = File("out.txt")
    outputFile.writeText("")

    for ((word, list) in result) {
        outputFile.appendText("$word, frequency: ${list.size} \n")

        for (item in list)
        {
            outputFile.appendText("$item \n")
        }

        outputFile.appendText("------------------------------\n")
        //println("$word - frequency: ${list.size}")
        //println(list)
    }

}
