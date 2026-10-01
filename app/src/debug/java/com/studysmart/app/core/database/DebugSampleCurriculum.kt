package com.studysmart.app.core.database

import com.studysmart.app.core.model.KeywordNormalizer

internal data class SampleKeyword(val text: String, val weight: Double = 1.0)
internal data class SampleQuestion(
    val id: String,
    val topicId: String?,
    val prompt: String,
    val answer: String,
    val keywords: List<SampleKeyword>,
)
internal data class SampleTopic(val id: String, val title: String, val questions: List<SampleQuestion>)
internal data class SampleChapter(
    val id: String,
    val subjectId: String,
    val title: String,
    val topics: List<SampleTopic>,
    val directQuestions: List<SampleQuestion>,
) {
    val questions: List<SampleQuestion> get() = topics.flatMap { it.questions } + directQuestions
}

/** Original debug practice material, not an official syllabus or examination bank.
 * Each row is prompt | reference answer | semicolon-separated grading keywords.
 * IDs are derived from permanent chapter/topic keys and row positions: append, do not reorder.
 */
internal object DebugSampleCurriculum {
    val chapters: List<SampleChapter> = listOf(
        chapter("dbms-design", "subject-bca-s1-dbms", "Database Design and SQL", listOf(
            topic("dbms-design", "relational", "Relational concepts", """
                What does a relational table represent? | A relation stores related data in rows and columns, with each row representing one record. | relation;rows;columns
                What is a tuple in a relation? | A tuple is one row representing a record whose fields correspond to attributes. | tuple;row;record
                What is an attribute in a relation? | An attribute is a named column describing one property of an entity. | attribute;column;property
                What is the domain of an attribute? | A domain is the set of permitted values for an attribute, such as valid integer ages. | domain;values;attribute
                How do degree and cardinality differ? | Degree counts columns in a relation, while cardinality counts rows. | degree;columns;cardinality;rows
                Why should a table avoid repeating groups? | Repeating groups violate first normal form; atomic values make filtering and updates simpler. | repeating groups;first normal form;atomic
                What is relational selection? | Selection filters rows using a condition while preserving the selected rows' columns. | selection;rows;condition
                What is relational projection? | Projection chooses columns from a relation and removes duplicate tuples in relational algebra. | projection;columns;duplicate
                When is a join useful? | A join combines rows from related tables using a matching condition such as a shared key. | join;tables;condition
                How is a relation different from a spreadsheet? | A relation follows a schema with defined domains and constraints; a spreadsheet permits more flexible cell content. | relation;schema;constraints
            """),
            topic("dbms-design", "keys", "Keys and constraints", """
                What is a primary key? | A primary key uniquely identifies each row and cannot contain null values. | primary key;row;null
                What is a candidate key? | A candidate key is a minimal set of attributes that uniquely identifies a row. | candidate key;minimal;attributes
                What is a foreign key? | A foreign key references a key in another or the same table to enforce referential integrity. | foreign key;references;referential integrity
                What is a composite key? | A composite key uses multiple columns together to uniquely identify a row. | composite key;columns;row
                Why use a unique constraint? | A unique constraint prevents duplicate values in a selected column or combination of columns. | unique constraint;duplicate;columns
                What does a NOT NULL constraint enforce? | A NOT NULL constraint requires a value for a column instead of a missing null value. | not null;value;column
                What does a CHECK constraint do? | A CHECK constraint validates a condition, such as price being positive, for inserted or updated data. | check constraint;condition;positive
                Why can deleting a parent row fail? | A foreign key may restrict deletion when a child row still references the parent. | foreign key;child;parent
                What does ON DELETE CASCADE mean? | ON DELETE CASCADE automatically deletes referencing child rows when their parent row is deleted. | on delete cascade;child;parent
                How do natural and surrogate keys differ? | A natural key has business meaning; a surrogate key is an artificial identifier independent of business data. | natural key;surrogate key;identifier
            """),
            topic("dbms-design", "normalization", "Normalization", """
                Why normalize database tables? | Normalization reduces redundancy and prevents insertion, update, and deletion anomalies. | normalization;redundancy;anomalies
                What is a functional dependency? | A functional dependency means a determinant attribute set uniquely determines another attribute set. | functional dependency;determinant;attribute
                What does first normal form require? | First normal form requires atomic values and no repeating groups in each table row. | first normal form;atomic;repeating groups
                What does second normal form remove? | Second normal form removes partial dependencies of non-key attributes on part of a candidate key. | second normal form;partial dependencies;candidate key
                What does third normal form remove? | Third normal form removes problematic transitive dependencies of non-key attributes on a key. | third normal form;transitive dependencies;key
                What is a partial dependency? | A partial dependency occurs when an attribute depends on only part of a composite candidate key. | partial dependency;attribute;composite
                Give an example of a transitive dependency. | If student ID determines department ID and department ID determines department name, department name has a transitive dependency on student ID. | student id;department id;transitive dependency
                What is a lossless decomposition? | A lossless decomposition splits a relation so a join reconstructs the original data without spurious rows. | lossless decomposition;join;spurious
                Why preserve dependencies during decomposition? | Dependency preservation allows constraints to be checked in individual tables without requiring a join. | dependency preservation;constraints;join
                When might denormalization be considered? | Denormalization adds controlled redundancy to improve read performance, but requires consistent updates. | denormalization;redundancy;updates
            """),
            topic("dbms-design", "sql", "SQL queries", """
                What does SELECT do in SQL? | SELECT retrieves specified columns from rows in a table or query result. | select;columns;rows
                How does WHERE affect a query? | WHERE filters rows using a condition before grouping and aggregation. | where;rows;condition
                How does ORDER BY affect output? | ORDER BY sorts query results in ascending or descending order by specified expressions. | order by;ascending;descending
                What is the purpose of GROUP BY? | GROUP BY collects rows with shared values so aggregate functions can calculate results per group. | group by;rows;aggregate
                How does HAVING differ from WHERE? | WHERE filters individual rows; HAVING filters groups after aggregation. | where;having;groups
                How do COUNT(*) and COUNT(column) differ? | COUNT(*) counts all rows; COUNT(column) counts only rows where that column is not null. | count;rows;null
                How does an INNER JOIN differ from a LEFT JOIN? | An INNER JOIN returns matching rows; a LEFT JOIN also keeps unmatched rows from the left table. | inner join;left join;unmatched
                Why use parameterized SQL queries? | Parameterized queries bind values separately from SQL syntax to help prevent injection. | parameterized;values;injection
                What does a transaction provide? | A transaction groups operations so they commit together or roll back together on failure. | transaction;commit;roll back
                Why use IS NULL rather than equals NULL? | IS NULL tests missing values; ordinary equality with NULL produces unknown rather than true. | is null;equality;unknown
            """),
        ), """
            How would you model students enrolling in courses? | Use student and course tables plus an enrollment table with foreign keys and a composite key for each student-course pair. | enrollment;foreign keys;composite key
            How can schema design and SQL improve data reliability together? | Schema constraints protect integrity, normalization reduces redundancy, and SQL transactions make related updates atomic. | constraints;normalization;transactions
        """),
        chapter("web-fundamentals", "subject-bca-s1-web", "Web Application Fundamentals", listOf(
            topic("web-fundamentals", "html", "HTML semantics", """
                What is semantic HTML? | Semantic HTML uses meaningful elements such as article, nav, and main to describe content structure. | semantic html;elements;structure
                Why use heading levels consistently? | Heading levels communicate document hierarchy and help screen reader users navigate sections. | heading;hierarchy;screen reader
                What is the role of the main element? | The main element identifies the dominant content of a document, excluding repeated navigation. | main;content;navigation
                Why associate a label with an input? | A label provides an accessible name for an input and improves activation and form usability. | label;input;accessible name
                What should alternative image text describe? | Alternative text should describe an image's purpose; decorative images use an empty alt attribute. | alternative text;decorative;alt
                How do links and buttons differ? | A link navigates to a resource; a button performs an action such as submitting a form. | link;button;action
                Why use a list element for related items? | A list communicates a collection and its structure through ul or ol elements containing li items. | list;collection;li
                What does a form element organize? | A form groups input controls for submitting user data to a specified destination. | form;input;data
                Why is document language important? | A lang attribute declares document language so assistive technology can choose pronunciation rules. | lang;language;pronunciation
                What is the purpose of table headers? | Table headers identify row or column relationships so data cells have understandable context. | headers;column;context
            """),
            topic("web-fundamentals", "css", "CSS layout", """
                What is the CSS box model? | The box model describes content surrounded by padding, border, and margin. | content;padding;margin
                How do padding and margin differ? | Padding is space inside the border; margin is space outside the border. | padding;margin;border
                What does border-box sizing change? | With border-box sizing, the declared width includes content, padding, and border. | border-box;width;padding
                What is Flexbox useful for? | Flexbox arranges items along a main axis and distributes space within a one-dimensional layout. | flexbox;main axis;space
                What is CSS Grid useful for? | CSS Grid organizes content in rows and columns for a two-dimensional layout. | grid;rows;columns
                What does a media query do? | A media query applies styles when conditions such as viewport width match. | media query;styles;viewport
                Why prefer relative units for text? | Relative units such as rem respect the root font size and support scalable text. | relative units;rem;font size
                What is CSS specificity? | Specificity ranks competing selectors to decide which declaration wins within the same cascade origin and layer. | specificity;selectors;declaration
                How can overflow affect accessibility? | Hidden overflow can clip text or controls, so layouts should accommodate enlarged content. | overflow;clip;content
                What is responsive design? | Responsive design adapts layout and content to different viewport sizes rather than assuming one fixed width. | responsive design;layout;viewport
            """),
            topic("web-fundamentals", "http", "HTTP and web requests", """
                What are the main parts of an HTTP request? | An HTTP request contains a method, target, headers, and optionally a body. | method;headers;body
                What is the usual purpose of GET? | GET requests a representation of a resource and should not intentionally change server state. | get;resource;state
                What is the usual purpose of POST? | POST sends data for processing and may create a resource or perform another server action. | post;data;resource
                What does a 404 status indicate? | A 404 status indicates that the server could not find the requested resource. | 404;server;resource
                What does a 500 status indicate? | A 500 status indicates an unexpected server error while handling the request. | 500;server;error
                Why use HTTPS? | HTTPS uses TLS to encrypt communication and authenticate the server using certificates. | https;tls;certificates
                What is an HTTP header? | A header carries metadata such as content type, authorization, or cache policy. | header;metadata;content type
                Why are cookies used? | Cookies store small values that a browser sends with matching requests, often supporting sessions. | cookies;browser;sessions
                What is caching in web communication? | Caching reuses stored responses according to freshness and validation rules to reduce requests. | caching;responses;validation
                What is the same-origin policy? | The same-origin policy restricts scripts from accessing resources across different scheme, host, or port origins. | same-origin policy;scripts;origins
            """),
            topic("web-fundamentals", "javascript", "JavaScript fundamentals", """
                How do let and const differ? | let permits reassignment of a binding; const prevents reassignment but does not make an object immutable. | let;const;reassignment
                What is a JavaScript function? | A function groups reusable statements and may accept parameters and return a value. | function;parameters;return
                What is an array? | An array is an ordered collection of values accessed using numeric indices. | array;collection;indices
                What is an object? | An object groups properties using keys associated with values. | object;properties;keys
                Why use strict equality? | Strict equality compares values without type coercion, avoiding some surprising conversions. | strict equality;values;coercion
                What is the DOM? | The DOM represents a document as nodes that scripts can inspect and modify. | dom;nodes;scripts
                What is an event listener? | An event listener runs a callback when an event such as a click occurs. | event listener;callback;click
                What does a Promise represent? | A Promise represents eventual completion or failure of an asynchronous operation. | promise;failure;asynchronous
                What does await do? | await pauses an async function until a Promise settles without blocking the main thread for that wait. | await;async;promise
                Why handle rejected requests? | Error handling catches rejected requests so the interface can display recovery information instead of silently failing. | error handling;requests;recovery
            """),
        ), """
            How can a web form remain usable on a narrow screen? | Use semantic labels, responsive layout, and readable text so inputs fit the viewport without clipping. | labels;responsive;viewport
            Describe a browser submitting a form to a server. | The browser collects form data, sends an HTTP request, and handles the server response with success or error feedback. | browser;http request;response
        """),
        chapter("ai-search", "subject-bca-s5-ai", "Search and Knowledge Representation", listOf(
            topic("ai-search", "problems", "Search problems", """
                What defines a search problem? | A search problem specifies an initial state, actions, a transition model, a goal test, and path costs. | initial state;actions;goal test
                What is a state space? | A state space is the set of possible states reachable under the problem's actions. | state space;states;actions
                What is a goal test? | A goal test checks whether a state satisfies the desired objective. | goal test;state;objective
                What is a transition model? | A transition model describes the resulting state after an action is applied. | transition model;state;action
                What is path cost? | Path cost is the sum of step costs along a sequence of actions. | path cost;sum;actions
                How do a state and a search node differ? | A state describes the world; a search node also stores path information such as its parent and cost. | state;search node;parent
                What is a search frontier? | A frontier holds generated nodes that have not yet been expanded. | frontier;nodes;expanded
                Why detect repeated states? | Repeated-state detection avoids redundant work and can prevent cycles from causing endless exploration. | detection;redundant;cycles
                What does search completeness mean? | Completeness means an algorithm finds a solution when one exists under its stated assumptions. | completeness;solution;assumptions
                What does search optimality mean? | Optimality means the algorithm finds a solution with the lowest path cost under its assumptions. | optimality;solution;path cost
            """),
            topic("ai-search", "uninformed", "Uninformed search", """
                How does breadth-first search expand nodes? | Breadth-first search expands the shallowest nodes first using a FIFO queue. | breadth-first search;shallowest;queue
                How does depth-first search expand nodes? | Depth-first search follows the deepest available node using a stack or recursion. | depth-first search;stack;recursion
                When is breadth-first search optimal? | Breadth-first search is optimal when step costs are equal and finds a shallowest goal. | breadth-first search;equal;goal
                Why can depth-first search fail to terminate? | Depth-first search can follow an infinite branch or cycle without reaching an available goal. | depth-first search;infinite;cycle
                What is uniform-cost search? | Uniform-cost search expands the node with lowest accumulated path cost using a priority queue. | uniform-cost search;path cost;priority queue
                What is depth-limited search? | Depth-limited search applies a maximum depth to prevent exploration beyond a chosen limit. | depth-limited search;depth;limit
                What is iterative deepening? | Iterative deepening repeats depth-limited search with increasing limits until a goal is found. | iterative deepening;depth-limited search;goal
                Why does iterative deepening repeat work? | Iterative deepening revisits shallow nodes at each depth limit to combine low memory use with completeness under finite branching. | iterative deepening;nodes;memory
                What is bidirectional search? | Bidirectional search explores from the initial state and the goal until their frontiers meet. | bidirectional search;initial state;frontiers
                What distinguishes uninformed search? | Uninformed search uses the problem definition without a heuristic estimate of distance to the goal. | uninformed search;heuristic;goal
            """),
            topic("ai-search", "heuristics", "Heuristic search", """
                What is a heuristic function? | A heuristic function estimates remaining cost from a state to a goal. | heuristic function;cost;goal
                What is an admissible heuristic? | An admissible heuristic never overestimates the true minimum remaining cost. | admissible heuristic;overestimates;cost
                What is a consistent heuristic? | A consistent heuristic satisfies a triangle inequality relating a state's estimate, step cost, and successor estimate. | consistent heuristic;step cost;successor
                How does greedy best-first search choose a node? | Greedy best-first search selects the smallest heuristic estimate, ignoring accumulated path cost in its priority. | greedy best-first search;heuristic;path cost
                How does A-star prioritize nodes? | A-star adds accumulated path cost g to heuristic estimate h and expands the node with lowest total f. | a-star;path cost;heuristic
                Why can a zero heuristic be useful? | A zero heuristic makes A-star behave like uniform-cost search while preserving a safe lower bound. | zero heuristic;a-star;uniform-cost search
                What is Manhattan distance? | Manhattan distance sums absolute coordinate differences along grid axes and can estimate four-direction movement cost. | manhattan distance;coordinate;grid
                Why compare heuristic accuracy? | A more accurate admissible heuristic can reduce expanded nodes, although computing it may require extra time. | admissible heuristic;nodes;time
                What is hill climbing? | Hill climbing repeatedly chooses an improving neighbor but can stop at a local optimum. | hill climbing;neighbor;local optimum
                How can a local optimum affect search? | A local optimum has no improving nearby move even when a better solution exists elsewhere. | local optimum;move;solution
            """),
            topic("ai-search", "knowledge", "Knowledge representation", """
                What is a knowledge base? | A knowledge base stores facts and rules that an inference procedure can use. | knowledge base;facts;rules
                What is a proposition? | A proposition is a statement that has a truth value, either true or false. | proposition;statement;truth
                What does logical conjunction mean? | Conjunction is true only when both combined propositions are true. | conjunction;propositions;true
                What does logical disjunction mean? | Inclusive disjunction is true when at least one proposition is true. | disjunction;proposition;true
                What does logical negation do? | Negation reverses a proposition's truth value from true to false or false to true. | negation;proposition;truth
                What is an implication? | An implication states that if an antecedent holds, a consequent must hold. | implication;antecedent;consequent
                What is inference? | Inference derives conclusions from facts and rules using a specified reasoning procedure. | inference;facts;rules
                How do forward and backward chaining differ? | Forward chaining starts with facts; backward chaining starts with a goal and seeks supporting facts. | forward chaining;backward chaining;facts
                Why use predicates instead of only propositions? | Predicates describe properties and relations of objects, allowing variables and quantifiers. | predicates;objects;quantifiers
                What is a universal quantifier? | A universal quantifier states that a predicate holds for every object in its domain. | universal quantifier;predicate;domain
            """),
        ), """
            How would you choose a route-search algorithm with varying road costs? | Define states and path costs, then use uniform-cost search or A-star with an admissible heuristic to seek a cheapest route. | path costs;uniform-cost search;admissible heuristic
            How can knowledge representation support an intelligent agent? | A knowledge base stores observations and rules so inference can derive conclusions that guide the agent's actions. | knowledge base;rules;inference
        """),
        chapter("network-fundamentals", "subject-bca-s5-net", "Networking Fundamentals", listOf(
            topic("network-fundamentals", "layers", "Layered models", """
                Why organize network protocols into layers? | Layers separate responsibilities and expose interfaces so protocols can evolve independently. | layers;interfaces;protocols
                What is encapsulation? | Encapsulation adds protocol headers around data as it passes down the network stack. | encapsulation;headers;data
                What is decapsulation? | Decapsulation removes headers as received data passes upward through the protocol stack. | decapsulation;headers;data
                What does the physical layer handle? | The physical layer transmits bits as signals over a communication medium. | physical layer;bits;signals
                What does the data-link layer handle? | The data-link layer transfers frames across a local link and uses link-layer addressing. | data-link layer;frames;link
                What does the network layer handle? | The network layer routes packets between networks using logical addresses. | network layer;packets;addresses
                What does the transport layer handle? | The transport layer supports process-to-process communication with services such as reliability and multiplexing. | transport layer;communication;multiplexing
                What does the application layer provide? | The application layer defines protocols used by applications, such as HTTP and DNS. | application layer;http;dns
                How do a switch and a router differ? | A switch typically forwards frames within a LAN; a router forwards packets between networks. | switch;router;packets
                What is protocol interoperability? | Interoperability allows different implementations to communicate by following shared protocol specifications. | interoperability;implementations;protocol
            """),
            topic("network-fundamentals", "addressing", "Addressing and subnetting", """
                What is an IPv4 address? | An IPv4 address is a 32-bit logical address used to identify a network interface. | ipv4;32-bit;interface
                What is an IPv6 address? | An IPv6 address is a 128-bit logical address with a much larger address space than IPv4. | ipv6;128-bit;address space
                What does a subnet mask identify? | A subnet mask separates an IPv4 address into network and host portions. | subnet mask;network;host
                What does CIDR notation express? | CIDR notation appends a prefix length indicating how many address bits identify the network. | cidr;prefix length;network
                How many total addresses are in an IPv4 /24 subnet? | A /24 prefix leaves eight host bits, giving 256 total addresses before host-use restrictions. | /24;host bits;256
                What is a default gateway? | A default gateway is the router used to reach destinations outside the local network when no more specific route applies. | default gateway;router;network
                What does ARP do in IPv4 networks? | ARP resolves a local IPv4 address to a link-layer MAC address. | arp;ipv4;mac
                Why use private IPv4 addresses? | Private IPv4 addresses support internal networks and are not globally routed on the public Internet. | private;networks;internet
                What does NAT do? | NAT translates network addresses, often allowing private hosts to share a public IPv4 address. | nat;private;public
                What does DHCP provide? | DHCP dynamically supplies IP configuration such as an address, subnet mask, and gateway. | dhcp;address;gateway
            """),
            topic("network-fundamentals", "transport", "TCP and UDP", """
                What service does TCP provide? | TCP provides a reliable ordered byte stream between applications. | tcp;reliable;byte stream
                What service does UDP provide? | UDP sends independent datagrams without built-in delivery or ordering guarantees. | udp;datagrams;ordering
                What is a transport port? | A port identifies a transport endpoint so data can be delivered to the intended application. | port;endpoint;application
                What happens during the TCP three-way handshake? | The client sends SYN, the server sends SYN-ACK, and the client sends ACK to establish the connection. | syn;syn-ack;ack
                Why does TCP use sequence numbers? | Sequence numbers identify byte positions and help detect missing or out-of-order data. | sequence numbers;byte;data
                What is a TCP acknowledgement? | An acknowledgement reports received data and helps the sender decide whether retransmission is needed. | acknowledgement;sender;retransmission
                What is flow control? | Flow control limits sending to match the receiver's available buffer capacity. | flow control;receiver;buffer
                What is congestion control? | Congestion control adjusts traffic to avoid overloading the network rather than only protecting a receiver buffer. | congestion control;traffic;network
                Why might an application choose UDP? | An application may choose UDP for low overhead or timeliness and implement any required reliability itself. | udp;overhead;reliability
                How do TCP and UDP preserve message boundaries? | UDP preserves datagram boundaries; TCP exposes a byte stream, so applications define their own message framing. | udp;tcp;framing
            """),
            topic("network-fundamentals", "applications", "Application protocols", """
                What does DNS resolve? | DNS resolves domain names to records such as IP addresses through a distributed naming system. | dns;domain names;addresses
                What is an HTTP response? | An HTTP response contains a status code, headers, and optionally a representation body. | status code;headers;body
                What does SMTP support? | SMTP transfers email between clients and mail servers or between mail servers. | smtp;email;servers
                What is IMAP used for? | IMAP accesses and manages email messages stored on a mail server. | imap;email;server
                What does SSH provide? | SSH provides encrypted remote access and authenticates communicating endpoints. | ssh;encrypted;access
                What is TLS? | TLS secures communication with encryption, integrity protection, and peer authentication. | tls;encryption;authentication
                Why can DNS caching improve performance? | DNS caching reuses answers until their time-to-live expires, reducing lookup traffic and delay. | dns;caching;time-to-live
                What is a client-server model? | In a client-server model, a client requests a service and a server processes the request. | client;server;request
                What is a peer-to-peer model? | In a peer-to-peer model, participating nodes can both request and provide resources. | peer-to-peer;nodes;resources
                Why is protocol authentication important? | Authentication verifies identity so a client does not blindly trust an impersonating endpoint. | authentication;identity;endpoint
            """),
        ), """
            Describe how a browser reaches a website on another network. | DNS finds an address, routing forwards packets through a gateway, and HTTPS protects the application exchange. | dns;gateway;https
            How would you distinguish receiver overload from network congestion? | Receiver overload calls for flow control, while congested network paths call for congestion control to reduce traffic. | flow control;congestion control;traffic
        """),
        chapter("programming-foundations", "subject-year1-foundations", "Algorithms and Programming Foundations", listOf(
            topic("programming-foundations", "algorithms", "Algorithm design", """
                What properties should an algorithm have? | An algorithm uses clear steps, accepts defined input, produces output, and terminates for its intended inputs. | steps;input;output
                What is pseudocode? | Pseudocode describes an algorithm using readable structured statements without requiring a specific programming language. | pseudocode;algorithm;statements
                What is a flowchart? | A flowchart diagrams steps and decisions using symbols connected by directed arrows. | flowchart;decisions;arrows
                Why specify preconditions? | Preconditions describe assumptions about input that must hold before an algorithm runs. | preconditions;input;assumptions
                Why specify postconditions? | Postconditions describe the result that must hold after an algorithm completes successfully. | postconditions;result;algorithm
                What is linear search? | Linear search checks items one by one until a target is found or the collection ends. | linear search;target;collection
                What requirement does binary search have? | Binary search requires sorted data and repeatedly halves the remaining search interval. | binary search;sorted;interval
                What does time complexity describe? | Time complexity describes how the number of operations grows with input size. | time complexity;operations;input size
                What does space complexity describe? | Space complexity describes how memory requirements grow with input size. | space complexity;memory;input size
                Why test boundary inputs? | Boundary inputs such as an empty collection or one element expose off-by-one and missing-case errors. | boundary;empty;errors
            """),
            topic("programming-foundations", "control", "Variables and control flow", """
                What is a variable? | A variable is a named binding or storage location associated with a value. | variable;named;value
                What is a data type? | A data type defines a set of values and the operations allowed on them. | data type;values;operations
                What does assignment do? | Assignment associates a value with a variable or updates a permitted storage location. | assignment;value;variable
                What is a Boolean expression? | A Boolean expression evaluates to true or false and often controls a decision. | boolean;true;false
                What does an if statement do? | An if statement selects a branch based on whether its condition is true. | if;branch;condition
                What is a loop? | A loop repeats statements while a condition holds or for items in a collection. | loop;statements;condition
                How do for and while loops commonly differ? | A for loop commonly iterates a collection or range; a while loop repeats while a condition is true. | for;while;condition
                What is an off-by-one error? | An off-by-one error processes one too many or one too few items because a boundary is incorrect. | off-by-one;items;boundary
                Why initialize an accumulator? | An accumulator needs an initial value such as zero before combining values during iteration. | accumulator;zero;iteration
                What makes a loop terminate? | A terminating loop updates state so its continuation condition eventually becomes false. | loop;state;condition
            """),
            topic("programming-foundations", "functions", "Functions and decomposition", """
                Why divide a program into functions? | Functions separate responsibilities, encourage reuse, and make testing smaller units easier. | functions;reuse;testing
                What is a parameter? | A parameter is a named input declared by a function definition. | parameter;input;function
                What is an argument? | An argument is a value supplied to a function when it is called. | argument;value;function
                What is a return value? | A return value is the result a function sends back to its caller. | return value;result;caller
                What is local scope? | Local scope limits a variable's visibility to a function or smaller block. | local scope;variable;visibility
                What is a pure function? | A pure function produces the same output for the same input and has no observable side effects. | pure function;output;side effects
                What is recursion? | Recursion occurs when a function calls itself to solve a smaller instance of a problem. | recursion;function;smaller
                Why does recursion need a base case? | A base case stops recursive calls when the simplest problem instance is reached. | base case;recursive;instance
                What is decomposition? | Decomposition breaks a large problem into smaller tasks with clear responsibilities. | decomposition;tasks;responsibilities
                Why document a function contract? | A contract specifies inputs, outputs, and assumptions so callers can use the function correctly. | contract;inputs;outputs
            """),
            topic("programming-foundations", "structures", "Basic data structures", """
                What is a stack? | A stack stores items in last-in first-out order using push and pop operations. | stack;push;pop
                What is a queue? | A queue stores items in first-in first-out order using enqueue and dequeue operations. | queue;enqueue;dequeue
                What is an array index? | An index identifies an element's position in an array and must stay within valid bounds. | index;array;bounds
                What is a linked list? | A linked list stores nodes connected by references rather than requiring contiguous element storage. | linked list;nodes;references
                What is a set? | A set stores distinct values and supports membership checks without duplicate elements. | set;membership;duplicate
                What is a map? | A map associates keys with values and supports lookup using a key. | map;keys;values
                What is a tree? | A tree organizes nodes hierarchically through parent-child relationships without cycles. | tree;nodes;cycles
                What is a graph? | A graph contains vertices and edges representing entities and their relationships. | graph;vertices;edges
                When is a queue useful in an application? | A queue is useful for processing tasks in arrival order, such as pending print jobs. | queue;tasks;arrival
                Why choose a data structure deliberately? | A data structure affects operation costs, memory use, and how clearly a program models its problem. | data structure;memory;operation
            """),
        ), """
            How would you find the largest value in a non-empty list? | Initialize a maximum from the first element, iterate over the remaining elements, and update the maximum when a larger value appears. | maximum;iterate;update
            How can functions and data structures improve an algorithm? | Functions separate tasks, while appropriate data structures simplify storage and operations, making an algorithm easier to test. | functions;data structures;algorithm
        """),
    ).also { validate(it) }

    internal fun validate(chapters: List<SampleChapter>) {
        val ids = chapters.flatMap { chapter ->
            listOf(chapter.id) + chapter.topics.map { it.id } + chapter.questions.map { it.id }
        }
        require(ids.distinct().size == ids.size) { "Sample curriculum IDs must be unique" }
        chapters.forEach { chapter ->
            require(chapter.subjectId.isNotBlank() && chapter.title.isNotBlank())
            require(chapter.topics.all { it.title.isNotBlank() })
            require(chapter.topics.map { it.title }.distinct().size == chapter.topics.size)
            require(chapter.topics.all { topic -> topic.questions.all { it.topicId == topic.id } })
            require(chapter.directQuestions.all { it.topicId == null })
            require(chapter.questions.map { it.prompt }.distinct().size == chapter.questions.size)
            chapter.questions.forEach { question ->
                require(question.prompt.isNotBlank() && question.answer.isNotBlank())
                require(question.keywords.size >= 3)
                val keywords = question.keywords.map { KeywordNormalizer.normalize(it.text) }
                require(keywords.all { it.isNotBlank() } && keywords.distinct().size == keywords.size)
                require(question.keywords.all { it.weight.isFinite() && it.weight > 0.0 })
                val answer = KeywordNormalizer.normalize(question.answer)
                require(keywords.all { answer.contains(it) }) { "Reference answer misses a keyword: ${question.id}" }
            }
        }
    }

    private fun chapter(
        key: String,
        subjectId: String,
        title: String,
        topics: List<SampleTopic>,
        directRows: String,
    ) = SampleChapter(
        id = "debug-sample-$key",
        subjectId = subjectId,
        title = "$title — Sample practice",
        topics = topics,
        directQuestions = questions("debug-sample-$key-direct", null, directRows),
    )

    private fun topic(chapterKey: String, key: String, title: String, rows: String): SampleTopic {
        val id = "debug-sample-$chapterKey-$key"
        return SampleTopic(id, title, questions(id, id, rows))
    }

    private fun questions(prefix: String, topicId: String?, rows: String): List<SampleQuestion> =
        rows.trimIndent().lineSequence().filter { it.isNotBlank() }.mapIndexed { index, row ->
            val fields = row.split(" | ")
            require(fields.size == 3) { "Sample question must contain prompt, answer, and keywords" }
            SampleQuestion(
                id = "$prefix-q${(index + 1).toString().padStart(2, '0')}",
                topicId = topicId,
                prompt = fields[0].trim(),
                answer = fields[1].trim(),
                keywords = fields[2].split(';').map { SampleKeyword(it.trim()) },
            )
        }.toList()
}
