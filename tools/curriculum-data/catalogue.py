"""Reviewed subject/outline inventory for the unpublished Stage 2 authoring draft.

The revised BCA subject names/codes come from the TU PDF, pages 6–9.
Semester 1–2 units follow the published detailed syllabus; later outlines are
original practice outlines explicitly authorized by the user, not official units.
Elective codes are transcribed literally, including TU's inconsistent slot codes.
"""

BCA_SOURCE = "https://portal.tu.edu.np/downloads/2025_11_27_15_22_42.pdf"
BBS_SOURCE = "https://www.fomecd.edu.np/documents/4%20Year%20BBS%20Course%20Structre%20with%20Details.pdf"

# semester | printed code | subject | original practice outline (later semesters)
BCA_ROWS = """
1|BCA 101|Computer Fundamentals and Applications|
1|BCA 102|Programming in C|
1|BCA 103|Digital Logic|
1|BCA 104|Mathematics-I|
1|BCA 105|Professional Communication and Ethics|
1|BCA 106|Hardware Workshop|
2|BCA 151|Discrete Structure|
2|BCA 152|Microprocessor and Computer Architecture|
2|BCA 153|OOP in Java|
2|BCA 154|Mathematics-II|
2|BCA 155|UX/UI Design|
2|BCA 156|Principles of Management|
3|BCA 201|Data Structure and Algorithms|Arrays, Lists, Stacks and Queues;Trees, Graphs and Algorithm Analysis
3|BCA 202|Database Management System|Relational Design and SQL;Transactions, Indexes and Database Integrity
3|BCA 203|Web Technology-I|Semantic HTML and Accessible CSS;JavaScript and Browser Interaction
3|BCA 204|System Analysis and Design|Requirements, Feasibility and System Models;System Design and Implementation Planning
3|BCA 205|Probability and Statistics|Probability Models and Distributions;Statistical Estimation and Hypothesis Testing
3|BCA 206|Applied Economics|Demand, Supply and Market Decisions;Costs, Production and Economic Policy
4|BCA 251|Operating Systems|Processes, Scheduling and Synchronization;Memory, Files and Storage Management
4|BCA 252|Software Engineering|Requirements and Software Design;Testing, Maintenance and Development Processes
4|BCA 253|Numerical Methods|Roots, Interpolation and Numerical Error;Numerical Integration and Differential Equations
4|BCA 254|Python Programming|Python Control Flow, Functions and Collections;Files, Exceptions and Object-Oriented Python
4|BCA 255|Web Technology-II|Server-Side Requests, Sessions and Data Access;Web Application Security and Deployment
4|BCA 256|Project-I|Project Scoping, Prototyping and Demonstration
5|BCA 301|Computer Network|Layered Protocols, Addressing and Routing;Transport Services and Network Troubleshooting
5|BCA 302|Artificial Intelligence|Agents, Search and Knowledge Representation;Reasoning, Learning and Responsible AI
5|BCA 303|Advance Java Programming|Java Database Access and Web Components;Concurrency and Java Application Architecture
5|BCA 304|MIS and e-Business|Management Information and Business Processes;Electronic Commerce, Payments and Security
5|BCA 305|Society and Technology|Digital Society, Access and Privacy;Technology, Work and Social Responsibility
5|BCA 306|Project-II|Requirements Traceability, Implementation and Project Evaluation
6|BCA 351|Computer Graphics and animation|Raster Graphics and Geometric Transformations;Viewing, Rendering and Animation
6|BCA 352|Mobile Programming|Android Components, Lifecycle and User Interfaces;Mobile Persistence, Networking and Platform Security
6|BCA 353|Cryptography and Network Security|Symmetric Encryption, Public Keys and Hashes;Authentication, Secure Protocols and Network Threats
6|BCA 354|Technical Writing|Technical Documents, Evidence and Audience;Reports, Instructions and Technical Presentations
6|BCA 355|Distributed System|Distributed Processes, Communication and Coordination;Replication, Consistency and Fault Tolerance
6|BCA 356|Project-III|Integration, Testing and Project Documentation
7|BCA 401|Cyber Security and Ethical Hacking|Threat Modelling and Defensive Security;Authorized Assessment, Reporting and Remediation
7|BCA 402|Software Project Management|Estimation, Scheduling and Project Risk;Quality, Configuration and Project Governance
7|BCA 403|Financial Accounting|Transactions, Ledgers and Trial Balance;Financial Statements and Accounting Adjustments
7|BCA 404|Project-IV|Project Validation, Delivery and Viva Preparation
7|BCA 404-I|Machine Learning|Supervised Learning and Model Evaluation;Unsupervised Learning, Features and Responsible Modelling
7|BCA 404-II|E-Commerce|Electronic Business Models and Customer Journeys;Payments, Fulfilment and Commerce Security
7|BCA 404-III|Database Administration|Database Storage, Access and Monitoring;Backup, Recovery and Operational Automation
7|BCA 404-IV|Linux|Linux Files, Processes and Shell Tools;Linux Permissions, Services and System Administration
7|BCA 405-I|Dotnet Technology|C# Types and Managed Execution;Data Access and .NET Web Applications
7|BCA 405-II|Business intelligence|Business Data Integration and Warehousing;Analytical Models, Dashboards and Decision Support
7|BCA 405-III|Software Testing and Quality Assurance|Test Design, Coverage and Defect Management;Quality Processes, Automation and Release Evidence
7|BCA 405-IV|Data Visualization|Visual Encoding, Chart Selection and Accessibility;Interactive Dashboards and Honest Data Communication
8|BCA 451|Cloud Computing|Cloud Service Models and Virtualization;Cloud Reliability, Security and Cost Management
8|BCA 452|Internship|Professional Practice, Evidence and Internship Reflection
8|BCA 453-I|Network Administration|Network Services, Addressing and Access Control;Network Monitoring, Procurement and Incident Response
8|BCA 453-II|E-governance|Digital Public Services and Citizen Access;Governance, Privacy and Public-Sector Interoperability
8|BCA 453-III|Database Programming|SQL Programming, Procedures and Triggers;Transactions, Application Data Access and Query Tuning
8|BCA 453-IV|Geographical Information System|Spatial Data, Coordinate Systems and Maps;Spatial Analysis and GIS Applications
8|BCA 454-I|Digital Marketing and SEO|Search Visibility, Content and Marketing Channels;Campaign Measurement, Ethics and Conversion
8|BCA 454-II|Image Processing|Image Representation, Filtering and Enhancement;Segmentation, Features and Image Analysis
8|BCA 454-III|Internet of Things|Sensors, Connectivity and IoT Architecture;IoT Data, Security and Deployment
8|BCA 454-IV|Data Mining and Data warehouse|Warehouse Design, ETL and OLAP;Pattern Discovery, Classification and Data Quality
"""

# Clean transcriptions of published unit/section titles, not inferred from OCR.
BCA_UNITS = {
    "BCA 101": "Introduction to Computer;Computer Hardware;Computer Software;Database Management System;Computer Network and Internet;Computer Security;Contemporary Technology",
    "BCA 102": "Introduction to C Programming Language and Basics;Input/Output and Control Structures;Functions, Arrays and String;Structures, Unions, and Enumerations;Pointers and Memory Management;File Handling, Command-Line Arguments, and Graphics",
    "BCA 103": "Digital Design Fundamentals and Number System;Boolean Algebra and Its Simplification;Combinational Logic Design;Sequential Logic Design;Counters and Registers",
    "BCA 104": "Logic, Relations, Functions, and Graphs;Sequence and Series;Matrices and Determinants;Analytical Geometry;Vector and Vector Space;Permutations and Combinations",
    "BCA 105": "Foundation of Professional Communication;Oral Communication;Writing Professionally;Interpersonal and Group Communication;Digital Communication;Professional Ethics",
    "BCA 106": "Introduction to Hardware (Electronic Components);Introduction of Networking Components;Desktop and Laptop Hardware Identification and Handling;PC and Laptop Assembly;Basic Network Device and Deployment;Basic Networking Configuration;Problem Solving Techniques;Maintenance and Upgrade;Final Practical Assessment",
    "BCA 151": "Set Theory;Logic and Propositional Calculus;Relations and Functions;Mathematical Reasoning and Proof Techniques;Combinatorics and Counting Principles;Graph Theory and Trees;Algebraic Structures",
    "BCA 152": "Introduction to Microprocessor;8085 Microprocessor;8086 Microprocessor;Basic Computer Architecture and Design;Microprogrammed Control Unit;Central Processing Unit;Computer Arithmetic;Input and Output Organization and Memory Organization",
    "BCA 153": "Introduction to Java and OOP Concept;Basics of Java Programming;Class and Objects in Java;Inheritance and Polymorphism;Exception Handling and Multithreading;File Handling in Java;Collections and Generics;Advanced OOP Concepts in Java",
    "BCA 154": "Limit and Continuity;Derivatives;Applications of Derivatives;Anti-derivative and Its Applications;Differential Equations;Computational Methods",
    "BCA 155": "Introduction to UX/UI Design;User Interaction Design;User Interface Design;UI Components;UI Design Considerations;Wireframing and Prototyping;Design Evaluations;Advanced Techniques: VUI and NLP Based UI",
    "BCA 156": "Introduction to Management;Planning and Decision Making;Organizing;Leading",
}

BCA_OUTLINE_PAGES = {
    "BCA 101": (10, 13), "BCA 102": (14, 19), "BCA 103": (20, 22),
    "BCA 104": (23, 26), "BCA 105": (27, 31), "BCA 106": (32, 40),
    "BCA 151": (41, 46), "BCA 152": (47, 50), "BCA 153": (51, 55),
    "BCA 154": (56, 59), "BCA 155": (60, 62), "BCA 156": (63, 65),
}

# Core/compulsory subjects by actual year; optional concentration papers are year 4.
BBS_YEARS = {
    1: ["MGT 201", "MGT 202", "MGT 207", "MGT 211", "MGT 213"],
    2: ["MGT 205", "MGT 209", "MGT 212", "MGT 223", "MGT 215"],
    3: ["MGT 204", "MGT 226", "MGT 217", "MGT 224", "MGT 214"],
    4: ["MGT 225", "MGT 221", "MGT 401"],
}


def revised_bca_subjects():
    rows = []
    for raw in BCA_ROWS.strip().splitlines():
        semester, code, name, practice = raw.split("|")
        number = int(semester)
        titles = BCA_UNITS.get(code, practice).split(";")
        assert all(titles), code
        elective = number >= 7 and "-" in code
        outline_pages = list(range(BCA_OUTLINE_PAGES[code][0], BCA_OUTLINE_PAGES[code][1] + 1)) if code in BCA_OUTLINE_PAGES else []
        rows.append({
            "courseId": "course-bca", "periodNumber": number, "code": code,
            "name": name, "syllabus": "TU BCA revised 2025", "source": BCA_SOURCE,
            "sourcePages": [6, 7, 8, 9], "cataloguePages": [6, 7, 8, 9],
            "outlinePages": outline_pages, "isElective": elective,
            "outlineKind": "published-unit" if number <= 2 else "original-practice",
            "chapters": titles,
            "notes": "Elective alternatives are not all compulsory." if elective else "",
        })
    return rows
